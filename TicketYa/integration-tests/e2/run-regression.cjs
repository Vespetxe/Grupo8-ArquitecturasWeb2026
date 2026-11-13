'use strict';
// Owns a NEW Compose project per run. Never accepts a base URL, datasource or existing project.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const http=require('node:http');
const net=require('node:net');
const {spawnSync}=require('node:child_process');
const {randomBytes,createHash}=require('node:crypto');
const {SERVICES,IMAGES,identity,composeSpec,validateSpec,assertContainer,assertVolume,urlFor}=require('./isolation.cjs');
const root=path.resolve(__dirname,'../../..');
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const args=process.argv.slice(2);
assert.ok(args.length===0 || (args.length===2 && args[0]==='--port'), 'Solo se admite --port 48080..48089');
const meta=identity(randomBytes(8).toString('hex'),args.length?Number(args[1]):48080);
const credentials=Object.fromEntries(['root','eventos','inventario','entradas','compras'].map(s=>[s,randomBytes(32).toString('hex')]));
const env={...process.env};
for(const key of Object.keys(env))if(key.startsWith('COMPOSE_')||key.startsWith('TY_REG_'))delete env[key];
for(const [s,value] of Object.entries(credentials))env[`TY_REG_${s.toUpperCase()}_PASSWORD`]=value;
const directory=path.join(root,'TicketYa/integration-tests/target/e2',meta.id);
const composePath=path.join(directory,'compose.json');
const report={run:meta.id,startedAt:new Date().toISOString(),...meta,requests:[],scenarios:[],images:{},result:'RUNNING'};
function safe(value){return String(value).replace(/\b[a-f0-9]{64}\b/gi,'[REDACTADO]');}
function docker(args,{input,sql=false}={}){
  const r=spawnSync('docker',args,{cwd:directory,env,input,encoding:'utf8',shell:false,maxBuffer:16*1024*1024});
  if(r.error || r.status!==0)throw new Error(sql?'Consulta/inicializacion SQL fallida; recursos conservados':safe(`Docker ${args[0]} fallo: ${r.error?.message||r.stderr}`));
  return r.stdout.trim();
}
function readSpec(){const spec=JSON.parse(fs.readFileSync(composePath,'utf8'));validateSpec(meta,spec);return spec;}
function compose(...args){
  readSpec();
  return docker(['compose','--env-file',process.platform==='win32'?'NUL':'/dev/null','--file',composePath,'--project-name',meta.project,...args]);
}
function inspect(service){return JSON.parse(docker(['inspect',`${meta.project}-${service}-1`]))[0];}
function attestMysql(){
  readSpec();
  assertContainer(meta,'mysql',inspect('mysql'),credentials);
  assertVolume(meta,JSON.parse(docker(['volume','inspect',meta.volume]))[0]);
}
function attest(){
  attestMysql();
  const ids=docker(['ps','-aq','--filter',`label=com.docker.compose.project=${meta.project}`]).split(/\s+/).filter(Boolean);
  assert.equal(ids.length,6,'Topologia inesperada');
  const containers=JSON.parse(docker(['inspect',...ids]));
  for(const s of SERVICES){const found=containers.filter(c=>c.Config.Labels['com.docker.compose.service']===s);assert.equal(found.length,1);assertContainer(meta,s,found[0],credentials);}
}
function sql(query,{initialize=false}={}){
  attestMysql();
  if(!initialize)assert.ok(query.trim().split(';').filter(x=>x.trim()).every(x=>/^SELECT\s/i.test(x.trim())),'Solo SELECT fuera del bootstrap');
  return docker(['exec','-i',`${meta.project}-mysql-1`,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --batch --skip-column-names --raw'],{input:query+'\n',sql:true});
}
function transport(method,requestPath,body){
  const url=urlFor(meta,requestPath);
  return new Promise((resolve,reject)=>{
    const payload=body===undefined?undefined:JSON.stringify(body);
    const headers={'Accept':'application/json'};
    if(payload!==undefined){headers['Content-Type']='application/json';headers['Content-Length']=Buffer.byteLength(payload);}
    if(requestPath.startsWith('/admin/')||requestPath.endsWith('/cancel'))headers['X-Admin-Id']='71001';
    if(requestPath.endsWith('/cancel'))headers['X-User-Role']='ADMIN';
    const req=http.request(url,{method,headers,timeout:25000},res=>{
      let data='';res.setEncoding('utf8');res.on('data',part=>{data+=part;});
      res.on('end',()=>{try{resolve({status:res.statusCode,body:data?JSON.parse(data):null});}catch{reject(new Error('Respuesta no JSON'));}});
    });
    // Native http does not consult proxy environment variables or follow redirects.
    req.on('timeout',()=>req.destroy(new Error('Timeout HTTP; no se repite la escritura')));
    req.on('error',reject);if(payload!==undefined)req.write(payload);req.end();
  });
}
async function api(method,p,body,expected=200){
  if(method!=='GET'||/^\/compras\/\d+\/entradas$/.test(p))attest();
  const r=await transport(method,p,body);
  report.requests.push({method,path:p,status:r.status});
  assert.equal(r.status,expected,`${method} ${p}: HTTP inesperado; no se reintenta`);
  return r.body;
}
const get=p=>api('GET',p);
const post=(p,b,status=200)=>api('POST',p,b,status);
const patch=p=>api('PATCH',p);
async function stock(e,n){const s=await get(`/disponibilidad/eventos/${e.event}/sectores/${e.sector}`);assert.equal(s.disponibles,n);assert.equal(s.capacidadTotal,2);return s;}
async function event(numbered,price,label){
  const name=`E2 ${meta.id} ${label}`;
  const v=await post('/api/v1/venues',{name,address:'Prueba E2 aislada',city:'Tandil',latitude:-37.328,longitude:-59.137},201);
  const s=await post(`/api/v1/venues/${v.venueId}/sectors`,{name:'Sector',type:numbered?'NUMERADO':'NO_NUMERADO',capacity:2,...(numbered?{rows:1,seatsPerRow:2}:{})},201);
  const e=await post('/api/v1/events',{name,eventType:'Recital',organizerId:71001,venueId:v.venueId,startDateTime:new Date(Date.now()+8*86400000).toISOString(),participantIds:[]},201);
  assert.equal(e.status,'BORRADOR');
  const prices=await post(`/api/v1/events/${e.eventId}/prices`,[{sectorId:s.sectorId,price}],201);
  assert.equal(prices[0].price,price);
  assert.equal((await patch(`/api/v1/events/${e.eventId}/publish`)).status,'PUBLICADO');
  const result={venue:v.venueId,event:e.eventId,sector:s.sectorId,numbered,price};
  const before=await stock(result,2);
  assert.equal((await patch(`/api/v1/events/${e.eventId}/publish`)).status,'PUBLICADO');
  assert.deepEqual(await stock(result,2),before);
  return result;
}
async function reserveAndBuy(e,client,quantity){
  const available=await stock(e,2);
  const seats=e.numbered?available.asientosLibres.slice(0,quantity).map(s=>s.idAsiento):[];
  const r=await post('/reservas',{idCliente:client,idEvento:e.event,idSector:e.sector,cantidad:quantity,...(e.numbered?{idsAsientos:seats}:{})},201);
  assert.equal(r.estado,'PENDIENTE');assert.ok(Date.parse(r.fechaExpiracion+'Z')>Date.now());
  await stock(e,2-quantity);
  const details=e.numbered?seats.map(asientoId=>({sectorId:e.sector,asientoId,cantidad:1})):[{sectorId:e.sector,cantidad:quantity}];
  const c=await post('/compras',{clienteId:client,eventoId:e.event,reservaId:r.idReserva,detalles:details},201);
  assert.equal(c.estado,'PENDIENTE');assert.equal(c.importeTotal,e.price*quantity);
  return {event:e.event,sector:e.sector,reservation:r.idReserva,purchase:c.id,client,quantity,seats};
}
async function completed(c){
  const p=await get(`/compras/${c.purchase}`);assert.equal(p.estado,'COMPLETADA');assert.equal(p.etapa,'FINALIZADA');
  assert.equal((await get(`/reservas/${c.reservation}`)).estado,'CONFIRMADA');
  const ts=await get(`/tickets?orderId=${c.purchase}`);assert.equal(ts.length,c.quantity);
  assert.equal(new Set(ts.map(t=>t.ticketId)).size,c.quantity);assert.equal(new Set(ts.map(t=>t.qr)).size,c.quantity);
  for(const t of ts){assert.equal(t.status,'EMITIDA');assert.equal(t.orderId,String(c.purchase));assert.equal(t.eventId,String(c.event));assert.equal(t.clientId,String(c.client));}
  if(c.seats.length)assert.deepEqual(ts.map(t=>Number(t.seatId)).sort(),[...c.seats].sort());
  return ts;
}
async function numberedRefundCancellation(){
  const e=await event(true,15000,'numerado');const c=await reserveAndBuy(e,72001,2);
  const payment={compraId:c.purchase,claveIdempotencia:`pago-${meta.id}-numerado`,medioPago:'TARJETA_DEBITO'};
  const p=await post('/pagos',payment,201);assert.equal(p.estado,'APROBADO');assert.equal(p.importe,30000);
  const tickets=await completed(c);await stock(e,0);
  const repeated=await post('/pagos',payment);assert.deepEqual(repeated,p);assert.deepEqual(await completed(c),tickets);
  const units=await get(`/compras/${c.purchase}/entradas`);assert.equal(units.length,2);
  const selected=units.find(u=>u.asientoId===c.seats[0]);const other=units.find(u=>u.entradaId!==selected.entradaId);
  assert.ok(selected&&other);assert.equal(selected.ticketId,tickets.find(t=>Number(t.seatId)===selected.asientoId).ticketId);
  const d=await post('/devoluciones',{compraId:c.purchase,clienteId:c.client,entradasIds:[selected.entradaId],motivo:'Regresion E2 parcial'},201);
  assert.equal(d.estado,'PENDIENTE');assert.equal(d.importeSolicitado,15000);
  const a=await post(`/admin/devoluciones/${d.id}/aprobar`,{motivo:'Regresion E2 aprobada'});assert.equal(a.estado,'APROBADA');assert.equal(a.importeReintegrado,0);
  assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.reintegros;`),'0');
  const refund={claveIdempotencia:`reintegro-${meta.id}`,importe:15000};
  const processed=await post(`/admin/devoluciones/${d.id}/procesar`,refund);
  assert.equal(processed.etapa,'FINALIZADA');assert.equal(processed.reintegro.estado,'COMPLETADO');assert.equal(processed.reintegro.importe,15000);
  const after=await get(`/tickets?orderId=${c.purchase}`);
  assert.equal(after.find(t=>t.ticketId===selected.ticketId).status,'ANULADA');assert.equal(after.find(t=>t.ticketId===other.ticketId).status,'EMITIDA');
  const free=await stock(e,1);assert.equal(free.asientosLibres[0].idAsiento,selected.asientoId);
  assert.deepEqual(await post(`/admin/devoluciones/${d.id}/procesar`,refund),processed);assert.deepEqual(await stock(e,1),free);
  assert.equal((await get(`/pagos/${p.id}`)).importeReintegrado,15000);
  assert.equal((await patch(`/api/v1/events/${e.event}/cancel`)).status,'CANCELADO');
  const finalPay=await get(`/pagos/${p.id}`);assert.equal(finalPay.estado,'REINTEGRADO_TOTAL');assert.equal(finalPay.importeReintegrado,30000);
  assert.equal((await get(`/compras/${c.purchase}`)).estado,'DEVUELTA_TOTAL');
  const cancelled=await get(`/tickets?orderId=${c.purchase}`);assert.equal(cancelled.length,2);assert.ok(cancelled.every(t=>t.status==='ANULADA'));await stock(e,0);
  const count=sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.reintegros;`);assert.equal(count,'2');
  assert.equal((await patch(`/api/v1/events/${e.event}/cancel`)).status,'CANCELADO');
  assert.deepEqual(await get(`/pagos/${p.id}`),finalPay);assert.deepEqual(await get(`/tickets?orderId=${c.purchase}`),cancelled);
  assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.reintegros;`),count);await stock(e,0);
  report.scenarios.push({name:'numerada_pago_idempotente_devolucion_parcial_cancelacion',...c,payment:p.id,refund:d.id});
}
async function rejected(){
  const e=await event(false,150000,'rechazo');const c=await reserveAndBuy(e,72002,1);
  const p=await post('/pagos',{compraId:c.purchase,claveIdempotencia:`rechazo-${meta.id}`,medioPago:'TARJETA_DEBITO'},201);
  assert.equal(p.estado,'RECHAZADO');assert.equal(p.importe,150000);
  assert.equal((await get(`/compras/${c.purchase}`)).estado,'FALLIDA');assert.equal((await get(`/reservas/${c.reservation}`)).estado,'LIBERADA');
  assert.deepEqual(await get(`/tickets?orderId=${c.purchase}`),[]);await stock(e,2);
  assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.ticket_batches WHERE order_id='${c.purchase}';`),'0');
  report.scenarios.push({name:'rechazo_y_liberacion',...c,payment:p.id});
}
async function recovered(){
  const e=await event(false,15000,'recuperacion');const c=await reserveAndBuy(e,72003,1);
  const key=`simular-timeout-${meta.id}`;const body={compraId:c.purchase,claveIdempotencia:key,medioPago:'TARJETA_DEBITO'};
  const p=await post('/pagos',body,201);assert.equal(p.estado,'RESULTADO_DESCONOCIDO');
  assert.equal((await get(`/compras/${c.purchase}`)).estado,'PROCESANDO');assert.deepEqual(await get(`/tickets?orderId=${c.purchase}`),[]);
  const query=`SELECT datos FROM ${meta.schemas.compras}.operaciones_simuladas WHERE clave='cobro:${key}';`;
  const before=sql(query);assert.equal(JSON.parse(before).aprobado,true);
  const done=await post('/pagos',body);assert.equal(done.id,p.id);assert.equal(done.estado,'APROBADO');assert.equal(done.referenciaExterna,JSON.parse(before).referencia);
  await completed(c);await stock(e,1);assert.equal(sql(query),before);
  assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.pagos WHERE compra_id=${c.purchase};`),'1');
  assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.ticket_batches WHERE order_id='${c.purchase}';`),'1');
  report.scenarios.push({name:'pago_incierto_recuperado',...c,payment:p.id});
}
async function waitHealthy(){
  const deadline=Date.now()+180000;
  while(Date.now()<deadline){const c=inspect('mysql');if(!c.State.Running)throw new Error('MySQL de prueba detenido');if(c.State.Health.Status==='healthy')return;await delay(2000);}
  throw new Error('MySQL de prueba no alcanzo healthy');
}
async function readiness(){
  const paths=['/api/v1/participants','/reservas?idCliente=72001','/tickets?clientId=72001','/compras?clienteId=72001'];
  const deadline=Date.now()+180000;
  attest();
  while(Date.now()<deadline){try{if((await Promise.all(paths.map(p=>transport('GET',p)))).every(r=>r.status===200))return;}catch{}await delay(2000);}
  throw new Error('Readiness HTTP no alcanzada; no se envio negocio');
}
async function main(){
  fs.mkdirSync(directory,{recursive:true});
  assert.equal(spawnSync('git',['check-ignore','--quiet',directory],{cwd:root}).status,0,'Informes no ignorados por Git');
  const spec=composeSpec(meta);validateSpec(meta,spec);fs.writeFileSync(composePath,JSON.stringify(spec,null,2)+'\n',{flag:'wx'});
  report.sourceRevision=spawnSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).stdout.trim();
  report.runnerSha256=createHash('sha256').update(fs.readFileSync(__filename)).digest('hex');
  assert.equal(docker(['ps','-aq','--filter',`label=com.docker.compose.project=${meta.project}`]),'','Proyecto ya existente');
  assert.equal(docker(['volume','ls','-q','--filter',`name=^${meta.volume}$`]),'','Volumen ya existente');
  await new Promise((resolve,reject)=>{const server=net.createServer();server.once('error',()=>reject(new Error('Puerto E2 ocupado; no se reutiliza ni detiene su propietario')));server.listen(meta.port,'127.0.0.1',()=>server.close(resolve));});
  for(const [s,image] of Object.entries(IMAGES))report.images[s]=JSON.parse(docker(['image','inspect',image]))[0].Id;
  compose('config','--quiet');
  console.log(`Regresion aislada: ${meta.project}; Gateway 127.0.0.1:${meta.port}`);
  compose('up','--detach','--no-build','--no-deps','--no-recreate','mysql');await waitHealthy();attestMysql();
  for(const db of Object.values(meta.schemas))assert.equal(sql(`SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${db}';`),'0');
  const statements=["SET SESSION sql_mode='NO_BACKSLASH_ESCAPES'"];
  for(const [s,db] of Object.entries(meta.schemas)){
    assert.match(credentials[s],/^[a-f0-9]{64}$/);
    const escaped=db.replaceAll('_','\\_');
    statements.push(`CREATE DATABASE \`${db}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci`,
      `CREATE USER 'reg_${s}'@'%' IDENTIFIED BY '${credentials[s]}'`, `GRANT ALL PRIVILEGES ON \`${escaped}\`.* TO 'reg_${s}'@'%'`);
  }
  sql(statements.join(';\n')+';',{initialize:true});
  compose('up','--detach','--no-build','--no-deps','--no-recreate','eventos','inventario','entradas','compras','gateway');
  await readiness();
  const count=sql(`SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN (${Object.values(meta.schemas).map(x=>`'${x}'`).join(',')});`);
  assert.equal(count,'26','Modelo inicial inesperado');
  for(const [s,table] of Object.entries({eventos:'event',inventario:'reserva',entradas:'tickets',compras:'compras'}))assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas[s]}.${table};`),'0');
  await numberedRefundCancellation();await rejected();await recovered();attest();
  assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.compras;`),'3');
  assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.pagos;`),'3');
  assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.tickets;`),'3');
  assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.ticket_batches;`),'2');
  report.result='PASS';
}
main().catch(error=>{report.result='FAIL';report.error=safe(error.message);process.exitCode=1;}).finally(()=>{
  report.finishedAt=new Date().toISOString();
  if(fs.existsSync(directory))fs.writeFileSync(path.join(directory,'report.json'),JSON.stringify(report,null,2)+'\n');
  console.log(`${report.result}: ${report.scenarios.length} escenarios; ${report.requests.length} solicitudes. Informe: ${path.join(directory,'report.json')}`);
  if(report.error)console.error(report.error);
  console.log('Recursos de prueba conservados. Este runner no ejecuta stop, down, rm ni limpieza.');
});
