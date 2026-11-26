'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../../..');
const projects={EventoCatalogo_Service:['grupo22/eventocatalogo_service',3],comprapago_service:['ar/edu/unicen/grupo8/comprasservice',3],entradas_service:['ar/edu/tudai/ticketya/servicio_entradas',1]};
function files(p){return fs.readdirSync(p,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(path.join(p,e.name)):[path.join(p,e.name)]);}
test('seven Feign interfaces implement exactly the 24 business HTTP operations',()=>{
 let count=0;
 for(const [project,[pkg,expected]]of Object.entries(projects)){
  const base=path.join(root,'TicketYa',project,'src/main/java',pkg);
  const clients=files(path.join(base,'feign')).filter(f=>f.endsWith('FeignClient.java'));assert.equal(clients.length,expected);
  for(const f of clients){const s=fs.readFileSync(f,'utf8');assert.match(s,/@(?:org\.springframework\.cloud\.openfeign\.)?FeignClient\(/);assert.match(s,/url="\$\{ticketya\./);count+=[...s.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping\(/g)].length;}
  const enabled=fs.readFileSync(path.join(base,'feign/FeignClientsConfiguration.java'),'utf8');for(const f of clients)assert(enabled.includes(path.basename(f,'.java')+'.class'));
  for(const f of files(base).filter(f=>f.endsWith('.java')&&!f.endsWith('ServiceTokens.java'))){const s=fs.readFileSync(f,'utf8');assert.doesNotMatch(s,/tokens\.client\(|\.\s*(get|post|patch|put|delete)\(\)\.uri\(/,f);}
  assert.match(fs.readFileSync(path.join(base,'feign/TargetFeignConfiguration.java'),'utf8'),/Retryer\.NEVER_RETRY/);
 }
 assert.equal(count,24);
});
test('Postman inventory covers 87 distinct controllers, including four authenticated probes',()=>{
 const inventory=require('./postman-contracts.cjs').inventory();assert.equal(inventory.length,87);
 assert.equal(inventory.filter(r=>r.public).length,60);assert.equal(inventory.filter(r=>!r.public).length,27);
 assert.equal(new Set(inventory.map(r=>r.service+' '+r.method+' '+r.route)).size,87);
 assert.equal(inventory.filter(r=>r.route==='/internal/security/readiness').length,4);
 const collection=require('./postman-contracts.cjs').generate();assert.equal(collection.item.filter(f=>['eventos','inventario','compras','entradas','identidad'].includes(f.name)).flatMap(f=>f.item).length,87);
 const text=JSON.stringify(collection);assert.doesNotMatch(text,/eyJ[\w-]+\.[\w-]+\.[\w-]+/);assert.doesNotMatch(text,/X-User-Role|X-Admin-Id/);
});
test('standalone Postman flow captures original identifiers and replays original payment/refund keys',()=>{
 const flow=require('./postman-flows.cjs').items();const byName=name=>flow.find(i=>i.name===name);
 assert.deepEqual(byName('Replay mismo pago').request.body,byName('Pago aprobado').request.body);
 assert.deepEqual(byName('Replay mismo reintegro').request.body,byName('Procesar reintegro').request.body);
 assert.deepEqual(byName('Recuperar intento original').request.body,byName('Pago incierto').request.body);
 assert.match(JSON.stringify(byName('Comprar A')),/purchaseId/);assert.match(JSON.stringify(byName('Pago aprobado')),/originalPaymentId/);
 assert.equal(byName('QR repetido rechazado').request.body.raw,byName('QR aceptado').request.body.raw);
});
test('every generated Postman script parses, including complementary GENERAL/NUMERADO fixtures',()=>{
 const vm=require('node:vm');const collection=require('./postman-contracts.cjs').generate();
 function check(item){for(const e of item.event||[])new vm.Script(e.script.exec.join('\n'));for(const child of item.item||[])check(child);}
 check(collection);
 const complementary=require('./postman-additional.cjs').items();const stock=complementary.find(i=>i.name==='Disponibilidad para cancelar sin pagar');
 for(const responseBody of [{disponibles:1,asientosLibres:null},{disponibles:1,asientosLibres:[{idAsiento:3}]}]){
  const values=new Map();const pm={test:()=>{},response:{text:()=>JSON.stringify(responseBody),json:()=>responseBody},collectionVariables:{set:(k,v)=>values.set(k,v)}};
  vm.runInNewContext(stock.event[0].script.exec.join('\n'),{pm});
  const reservation=JSON.parse('{"cantidad":1'+values.get('reserveSeats')+'}');
  assert.deepEqual(reservation.idsAsientos,responseBody.asientosLibres?[3]:undefined);
 }
});
test('isolated Feign tag selects all five application images without falling back to dev',()=>{
 const cp=require('node:child_process');const file=path.join(__dirname,'isolation.cjs');
 const images=JSON.parse(cp.execFileSync(process.execPath,['-e','console.log(JSON.stringify(require('+JSON.stringify(file)+').IMAGES))'],{encoding:'utf8',env:{...process.env,TY_FEIGN_IMAGE_TAG:'feign-1234abcd'}}));
 for(const image of Object.values(images))assert.match(image,/:feign-1234abcd$/);
});
