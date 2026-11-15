'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {identity,spec,validate}=require('./security-isolation.cjs');
const meta=identity('abcdef1234567890');
test('business runner refuses anonymous execution before Docker or data creation',()=>{
 const result=require('node:child_process').spawnSync(process.execPath,[path.join(__dirname,'run-regression.cjs')],{encoding:'utf8'});
 assert.equal(result.status,2);assert.match(result.stderr,/propiedad/);
});
test('seven services and public key only on resource servers',()=>{const s=spec(meta);validate(meta,s);assert.equal(Object.keys(s.services).length,7);for(const name of ['gateway','compras','entradas','eventos','inventario'])assert.ok(s.services[name].volumes[0].source.endsWith('public.pem'));});
test('reject persistent ports, credentials topology, external volumes and private backend mounts',()=>{
 for(const change of [s=>s.services.mysql.ports=['3306:3306'],s=>s.services.compras.environment.DB_URL='jdbc:mysql://mysql/ticketya_e2_docker_compras',s=>s.volumes.mysql_data.external=true,s=>s.services.gateway.volumes[0].source='private.pem',s=>s.services.gateway.environment.SPRING_APPLICATION_JSON='{}']){const s=spec(meta);change(s);assert.throws(()=>validate(meta,s));}
 assert.throws(()=>identity(meta.id,38080));
});
test('JWT trust and common policy stay synchronized; local feature rules stay scoped',()=>{
 const projects={'api-gateway':'ar/edu/ticketya/gateway','EventoCatalogo_Service':'grupo22/eventocatalogo_service','inventarioReserva_service':'example/microservicio_inventario_reservas','entradas_service':'ar/edu/tudai/ticketya/servicio_entradas','comprapago_service':'ar/edu/unicen/grupo8/comprasservice'};
 for(const file of ['JwtTrust.java','RoutePolicy.java']){
  let expected;const extensions={};
  for(const [dir,pkg]of Object.entries(projects)){
   let text=fs.readFileSync(path.resolve(__dirname,'../../',dir,'src/main/java',pkg,'security',file),'utf8').replace(/^package[^\n]+\n/,'').replace(/\r\n/g,'\n');
   if(file==='RoutePolicy.java')for(const [name,begin,end,owners]of [
    ['queue','   // Queue extension','   if(post&&p.equals("/reservas"))',['api-gateway','inventarioReserva_service']],
    ['qr','   // QR extension','   // End QR extension.',['api-gateway','entradas_service']],
    ['recommendations','   // Recommendations extension','   // End recommendations extension.',['api-gateway','EventoCatalogo_Service']],
    ['notifications','   // Notifications extension','   // End notifications extension.',['api-gateway','comprapago_service']],
    ['notificationDelivery','   // Notification delivery extension','   // End notification delivery extension.',['comprapago_service']],
    ['reports','   // Reports extension','   // End reports extension.',['api-gateway','EventoCatalogo_Service']],
    ['history','   // Technical history extension','   // End history extension.',['comprapago_service']]]){
     const at=text.indexOf(begin);if(!owners.includes(dir)){assert.equal(at,-1);continue;}
     assert.ok(at>=0);let until=text.indexOf(end,at);assert.ok(until>at);if(name!=='queue')until+=end.length+1;
     const extension=text.slice(at,until);if(extensions[name])assert.equal(extension,extensions[name]);else extensions[name]=extension;
     if(name==='history')assert.match(extension,/service\(a,"eventos",owner,"purchases.history.read"\)/);
     text=text.slice(0,at)+text.slice(until);
   }
   if(expected)assert.equal(text,expected);else expected=text;
  }
 }
});

test('technical OAuth manager implementation is identical except caller and destinations',()=>{
 const projects={'EventoCatalogo_Service':'grupo22/eventocatalogo_service','entradas_service':'ar/edu/tudai/ticketya/servicio_entradas','comprapago_service':'ar/edu/unicen/grupo8/comprasservice'};
 let expected;
 for(const [dir,pkg]of Object.entries(projects)){
  const text=fs.readFileSync(path.resolve(__dirname,'../../',dir,'src/main/java',pkg,'security/ServiceTokens.java'),'utf8').replace(/^package[^\n]+\n/,'').replace(/\r\n/g,'\n')
   .replace(/service:(eventos|compras|entradas)/g,'service:CALLER').replace(/for\(String target:List\.of\([^)]*\)\)/,'for(String target:TARGETS)')
   .replace(/\.clientId\("(eventos|compras|entradas)-to-"\+target\)/,'.clientId("CALLER-to-"+target)')
   .replace(/requestedScopes\("(eventos|compras|entradas)",target\)/,'requestedScopes("CALLER",target)')
   .replaceAll(pkg.replaceAll('/','.')+'.observability.','OBSERVABILITY.')
   .replaceAll(pkg.replaceAll('/','.')+'.timing.','TIMING.');
  if(expected)assert.equal(text,expected);else expected=text;
 }
});
