'use strict';
const assert=require('node:assert/strict'),crypto=require('node:crypto'),http=require('node:http');
const {urlFor}=require('./isolation.cjs');
module.exports=async function commercial({transport,finalValidation,meta,sql,attest,adminPassword,provisioned,queue,qr,notifications,reports,cache,prepareLoad,recommendations,rememberQr=()=>{},faults,progress=()=>{}}){
 const report={requests:[],scenarios:[]};let clients=[],organizers=[],admin,access,currentClient;
 function defaultActor(method,p){if(p.startsWith('/admin/')||p.endsWith('/cancel'))return admin;if(p.startsWith('/api/v1/')&&method!=='GET')return organizers[0];return currentClient;}
 async function api(method,p,body,expected=200,actor=defaultActor(method,p),extra={},checkTopology=true){
  if(checkTopology)attest();const url=urlFor(meta,p);const payload=body===undefined?undefined:JSON.stringify(body);
  const started=performance.now();
  const response=transport?await transport({method,url,body,headers:{Accept:"application/json",...extra,...(actor?{Authorization:"Bearer "+actor.token}:{})},expected}):await new Promise((resolve,reject)=>{
   const headers={Accept:'application/json',...extra,...(actor?{Authorization:'Bearer '+actor.token}:{}),...(payload===undefined?{}:{'Content-Type':'application/json','Content-Length':Buffer.byteLength(payload)})};
   const req=http.request(url,{method,headers,timeout:25000},res=>{let data='';res.setEncoding('utf8');res.on('data',p=>data+=p);res.on('end',()=>{try{resolve({status:res.statusCode,correlationId:res.headers["x-correlation-id"],body:data?JSON.parse(data):null});}catch{reject(new Error('Non-JSON HTTP response'));}});});
   req.on('error',reject);req.on('timeout',()=>req.destroy(new Error('HTTP timeout; no automatic write retry')));if(payload!==undefined)req.write(payload);req.end();
  });
  report.requests.push({durationMs:Math.round((performance.now()-started)*100)/100,method,path:p,status:response.status,correlationId:response.correlationId,expected,actor:actor?.id});progress(report);
  if(Array.isArray(expected)){assert.ok(expected.includes(response.status),'Unexpected fault response status');return response;}
  assert.equal(response.status,expected,method+' '+p+': expected '+expected+' received '+response.status);return response.body;
 }
 const reportChecks=reports?require('./reports-smoke.cjs')({meta,sql,api,clients:()=>clients,organizers:()=>organizers,admin:()=>admin,access:()=>access,controls:recommendations}):null;
 const get=p=>api('GET',p),post=(p,b,s=200)=>api('POST',p,b,s),patch=p=>api('PATCH',p);
 async function login(email,password){const login=await api('POST','/auth/login',{email,password},200,null);const token=login.access_token;const me=await api('GET','/auth/me',undefined,200,{token});assert.ok(me.id>0);return {id:me.id,token};}
 for(let n=0;n<3;n++){const email='business-client-'+n+'-'+meta.id+'@example.test',password=crypto.randomBytes(32).toString('hex');await api('POST','/auth/register',{email,password},201,null);clients.push({...await login(email,password),email,password});}
 admin=await login('admin-'+meta.id+'@example.test',adminPassword);
 for(const user of provisioned){const actor=await login(user.email,user.password);if(user.role==='ORGANIZADOR')organizers.push(actor);else access=actor;}
 currentClient=clients[0];
 await api('POST','/api/v1/events',{name:'spoof',eventType:'Recital',organizerId:organizers[1].id,venueId:999,startDateTime:new Date(Date.now()+8*86400000).toISOString()},403,organizers[0]);
 await api('GET','/tickets?clientId='+clients[0].id,undefined,200,clients[0]).then(list=>assert.deepEqual(list,[]));
async function stock(e,n){const s=await get(`/disponibilidad/eventos/${e.event}/sectores/${e.sector}`);assert.equal(s.disponibles,n);assert.equal(s.capacidadTotal,2);return s;}
async function event(numbered,price,label,options={}){
  const name=`E2 ${meta.id} ${label}`;
  const v=await post('/api/v1/venues',{name,address:'Prueba E2 aislada',city:options.city||'Tandil',latitude:-37.328,longitude:-59.137},201);
  const s=await post(`/api/v1/venues/${v.venueId}/sectors`,{name:'Sector',type:numbered?'NUMERADO':'NO_NUMERADO',capacity:2,...(numbered?{rows:1,seatsPerRow:2}:{})},201);
  const e=await post('/api/v1/events',{name,eventType:options.type||'Recital',organizerId:organizers[0].id,venueId:v.venueId,startDateTime:new Date(Date.now()+8*86400000).toISOString(),participantIds:options.participantIds||[]},201);
  assert.equal(e.status,'BORRADOR');
  await api('PATCH', '/api/v1/events/'+e.eventId+'/publish',undefined,404,organizers[1]);
  await api('POST','/api/v1/events/'+e.eventId+'/prices',[{sectorId:s.sectorId,price}],404,organizers[1]);
  await api('POST','/api/v1/venues/'+v.venueId+'/sectors',{name:'foreign',type:'NO_NUMERADO',capacity:2},404,organizers[1]);
  await api('GET','/api/v1/events/'+e.eventId,undefined,404,organizers[1]);
  const prices=await post(`/api/v1/events/${e.eventId}/prices`,[{sectorId:s.sectorId,price}],201);
  assert.equal(prices[0].price,price);
  assert.equal((await patch(`/api/v1/events/${e.eventId}/publish`)).status,'PUBLICADO');
  const result={venue:v.venueId,event:e.eventId,sector:s.sectorId,numbered,price};
  const before=await stock(result,2);
  if(reportChecks){if(options.expectReportUnavailable)await api('GET','/api/v1/events/'+result.event+'/report',undefined,503,organizers[0]);else await reportChecks.check(result,{sold:0,returned:0,gross:0,refund:0,net:0,occupancy:0});}
  assert.equal((await patch(`/api/v1/events/${e.eventId}/publish`)).status,'PUBLICADO');
  assert.deepEqual(await stock(result,2),before);
  return result;
}
async function reserveAndBuy(e,client,quantity){
  currentClient=clients.find(c=>c.id===client);
  const available=await stock(e,2);
  const seats=e.numbered?available.asientosLibres.slice(0,quantity).map(s=>s.idAsiento):[];
  const r=await post('/reservas',{idCliente:client,idEvento:e.event,idSector:e.sector,cantidad:quantity,...(e.numbered?{idsAsientos:seats}:{})},201);
  assert.equal(r.estado,'PENDIENTE');assert.ok(Date.parse(r.fechaExpiracion+'Z')>Date.now());
  await stock(e,2-quantity);
  const details=e.numbered?seats.map(asientoId=>({sectorId:e.sector,asientoId,cantidad:1})):[{sectorId:e.sector,cantidad:quantity}];
  await api('POST','/reservas',{idCliente:clients.find(c=>c.id!==client).id,idEvento:e.event,idSector:e.sector,cantidad:1},403,currentClient);
  await api('GET','/reservas/'+r.idReserva,undefined,404,clients.find(c=>c.id!==client));
  await api('GET','/reservas?idCliente='+client,undefined,403,clients.find(c=>c.id!==client));
  const c=await post('/compras',{clienteId:client,eventoId:e.event,reservaId:r.idReserva,detalles:details},201);
  assert.equal(c.estado,'PENDIENTE');assert.equal(c.importeTotal,e.price*quantity);
  if(reportChecks)await reportChecks.check(e,{sold:0,returned:0,gross:0,refund:0,net:0,occupancy:0});
  const outsider=clients.find(x=>x.id!==client);
  const detailsForSpoof={clienteId:outsider.id,eventoId:e.event,reservaId:r.idReserva,detalles:details};
  await api('POST','/compras',detailsForSpoof,404,outsider);
  await api('POST','/compras',{...detailsForSpoof,clienteId:client},403,outsider);
  for(const path of ['/compras/'+c.id,'/compras/'+c.id+'/estado','/compras/'+c.id+'/detalle','/compras/'+c.id+'/entradas','/tickets?orderId='+c.id])await api('GET',path,undefined,404,outsider);
  await api('GET','/tickets?orderId='+c.id,undefined,200,currentClient).then(list=>assert.deepEqual(list,[]));
  await api('GET','/tickets?clientId='+client,undefined,403,outsider);
  await api('GET','/compras?clienteId='+client,undefined,403,outsider);
  await api('POST','/compras/'+c.id+'/cancelar',{},404,outsider);
  await api('POST','/pagos',{compraId:c.id,claveIdempotencia:'foreign-'+meta.id,medioPago:'TARJETA_DEBITO'},404,outsider);
  await api('POST','/pagos',{compraId:c.id,claveIdempotencia:'admin-'+meta.id,medioPago:'TARJETA_DEBITO'},403,admin);
  await api('GET','/tickets?orderId='+c.id,undefined,403,access);
  await api('POST','/api/v1/venues',{name:'wrong-role',address:'test'},403,currentClient);
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
  const e=await event(true,15000,'numerado');const c=await reserveAndBuy(e,clients[0].id,2);
  const payment={compraId:c.purchase,claveIdempotencia:`pago-${meta.id}-numerado`,medioPago:'TARJETA_DEBITO'};
  const p=await post('/pagos',payment,201);assert.equal(p.estado,'APROBADO');assert.equal(p.importe,30000);
  const tickets=await completed(c);await stock(e,0);
  if(reportChecks)await reportChecks.check(e,{sold:2,returned:0,gross:30000,refund:0,net:30000,occupancy:100});
  await api('POST','/pagos',payment,404,clients[1]);
  await api('GET','/pagos/'+p.id,undefined,404,clients[1]);
  await api('GET','/pagos?compraId='+c.purchase,undefined,404,clients[1]);
  const repeated=await post('/pagos',payment);assert.deepEqual(repeated,p);assert.deepEqual(await completed(c),tickets);
  const units=await get(`/compras/${c.purchase}/entradas`);assert.equal(units.length,2);
  const selected=units.find(u=>u.asientoId===c.seats[0]);const other=units.find(u=>u.entradaId!==selected.entradaId);
  assert.ok(selected&&other);assert.equal(selected.ticketId,tickets.find(t=>Number(t.seatId)===selected.asientoId).ticketId);
  await api('POST','/devoluciones',{compraId:c.purchase,clienteId:clients[1].id,entradasIds:[selected.entradaId],motivo:'foreign'},404,clients[1]);
  const d=await post('/devoluciones',{compraId:c.purchase,clienteId:c.client,entradasIds:[selected.entradaId],motivo:'Regresion E2 parcial'},201);
  await api('GET','/devoluciones/'+d.id,undefined,404,clients[1]);
  await api('POST','/admin/devoluciones/'+d.id+'/aprobar',{motivo:'forged headers'},403,currentClient,{'X-Admin-Id':String(admin.id),'X-User-Role':'ADMIN'});
  assert.equal(d.estado,'PENDIENTE');assert.equal(d.importeSolicitado,15000);
  const a=await post(`/admin/devoluciones/${d.id}/aprobar`,{motivo:'Regresion E2 aprobada'});assert.equal(a.estado,'APROBADA');assert.equal(a.importeReintegrado,0);
  assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.reintegros;`),'0');
  const refund={claveIdempotencia:`reintegro-${meta.id}`,importe:15000};
  const processed=await post(`/admin/devoluciones/${d.id}/procesar`,refund);
  await api('POST','/admin/devoluciones/'+d.id+'/procesar',refund,403,currentClient);
  assert.equal(processed.etapa,'FINALIZADA');assert.equal(processed.reintegro.estado,'COMPLETADO');assert.equal(processed.reintegro.importe,15000);
  const after=await get(`/tickets?orderId=${c.purchase}`);
  assert.equal(after.find(t=>t.ticketId===selected.ticketId).status,'ANULADA');assert.equal(after.find(t=>t.ticketId===other.ticketId).status,'EMITIDA');
  if(reportChecks)await reportChecks.check(e,{sold:2,returned:1,gross:30000,refund:15000,net:15000,occupancy:50});
  const free=await stock(e,1);assert.equal(free.asientosLibres[0].idAsiento,selected.asientoId);
  assert.deepEqual(await post(`/admin/devoluciones/${d.id}/procesar`,refund),processed);assert.deepEqual(await stock(e,1),free);
  assert.equal((await get(`/pagos/${p.id}`)).importeReintegrado,15000);
  if(qr){rememberQr(after.map(t=>t.qr));report.qr=await require('./qr-smoke.cjs')({meta,sql,attest,api,clients,organizers,admin,access,event:e.event,active:after.find(t=>t.status==='EMITIDA'),annulled:after.find(t=>t.status==='ANULADA')});}
  assert.equal((await patch(`/api/v1/events/${e.event}/cancel`)).status,'CANCELADO');
  if(qr){assert.equal(sql(`SELECT CONCAT(validated_at,':',validated_by) FROM ${meta.schemas.entradas}.tickets WHERE ticket_id='${report.qr.usedTicketId}';`),report.qr.persistedAdmission);const blocked=await api('POST',`/accesos/eventos/${e.event}/validaciones`,{qr:after.find(t=>t.status==='EMITIDA').qr},409,access);assert.equal(blocked.code,'EVENT_BLOCKED');report.qr.cancellationPreservesAdmission=true;}
  const finalPay=await get(`/pagos/${p.id}`);assert.equal(finalPay.estado,'REINTEGRADO_TOTAL');assert.equal(finalPay.importeReintegrado,30000);
  assert.equal((await get(`/compras/${c.purchase}`)).estado,'DEVUELTA_TOTAL');
  const cancelled=await get(`/tickets?orderId=${c.purchase}`);assert.equal(cancelled.length,2);assert.ok(cancelled.every(t=>t.status==='ANULADA'));await stock(e,0);
  assert.equal(sql(`SELECT cancellation_authorized_admin_id FROM ${meta.schemas.eventos}.event WHERE event_id=${e.event};`),String(admin.id));
  const originalAuthority=sql(`SELECT CONCAT(cancellation_authorized_admin_id,':',cancellation_authorization_jti,':',cancellation_authorized_at) FROM ${meta.schemas.eventos}.event WHERE event_id=${e.event};`);
  const count=sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.reintegros;`);assert.equal(count,'2');
  assert.equal((await patch(`/api/v1/events/${e.event}/cancel`)).status,'CANCELADO');
  assert.deepEqual(await get(`/pagos/${p.id}`),finalPay);assert.deepEqual(await get(`/tickets?orderId=${c.purchase}`),cancelled);
  assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.reintegros;`),count);
  assert.equal(sql(`SELECT CONCAT(cancellation_authorized_admin_id,':',cancellation_authorization_jti,':',cancellation_authorized_at) FROM ${meta.schemas.eventos}.event WHERE event_id=${e.event};`),originalAuthority);await stock(e,0);
  if(reportChecks)await reportChecks.check(e,{sold:2,returned:2,gross:30000,refund:30000,net:0,occupancy:0});
  report.originalCancellationAuthorityPreserved=true;
  report.scenarios.push({name:'numerada_pago_idempotente_devolucion_parcial_cancelacion',...c,payment:p.id,refund:d.id});
}
async function rejected(){
  const e=await event(false,150000,'rechazo');const c=await reserveAndBuy(e,clients[1].id,1);
  const p=await post('/pagos',{compraId:c.purchase,claveIdempotencia:`rechazo-${meta.id}`,medioPago:'TARJETA_DEBITO'},201);
  assert.equal(p.estado,'RECHAZADO');assert.equal(p.importe,150000);
  assert.equal((await get(`/compras/${c.purchase}`)).estado,'FALLIDA');assert.equal((await get(`/reservas/${c.reservation}`)).estado,'LIBERADA');
  assert.deepEqual(await get(`/tickets?orderId=${c.purchase}`),[]);await stock(e,2);
  assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.ticket_batches WHERE order_id='${c.purchase}';`),'0');
  if(reportChecks)await reportChecks.check(e,{sold:0,returned:0,gross:0,refund:0,net:0,occupancy:0});
  report.scenarios.push({name:'rechazo_y_liberacion',...c,payment:p.id});
}
async function recovered(){
  const e=await event(false,15000,'recuperacion');const c=await reserveAndBuy(e,clients[2].id,1);
  const key=`simular-timeout-${meta.id}`;const body={compraId:c.purchase,claveIdempotencia:key,medioPago:'TARJETA_DEBITO'};
  const p=await post('/pagos',body,201);assert.equal(p.estado,'RESULTADO_DESCONOCIDO');
  if(reportChecks)await reportChecks.check(e,{sold:0,returned:0,gross:0,refund:0,net:null,occupancy:0,complete:false});
  assert.equal((await get(`/compras/${c.purchase}`)).estado,'PROCESANDO');assert.deepEqual(await get(`/tickets?orderId=${c.purchase}`),[]);
  const query=`SELECT datos FROM ${meta.schemas.compras}.operaciones_simuladas WHERE clave='cobro:${key}';`;
  const before=sql(query);assert.equal(JSON.parse(before).aprobado,true);
  const done=await post('/pagos',body);assert.equal(done.id,p.id);assert.equal(done.estado,'APROBADO');assert.equal(done.referenciaExterna,JSON.parse(before).referencia);
  await completed(c);await stock(e,1);assert.equal(sql(query),before);
  assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.pagos WHERE compra_id=${c.purchase};`),'1');
  assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.ticket_batches WHERE order_id='${c.purchase}';`),'1');
  if(reportChecks)await reportChecks.check(e,{sold:1,returned:0,gross:15000,refund:0,net:15000,occupancy:50});
  report.scenarios.push({name:'pago_incierto_recuperado',...c,payment:p.id});
}

 if(recommendations){recommendations.stop('mongo');try{await numberedRefundCancellation();await rejected();await recovered();report.mongoCommercialOutageVerified=true;}finally{if(recommendations.available())await recommendations.start('mongo');}}
 else{await numberedRefundCancellation();await rejected();await recovered();}
 const concurrentStart=report.requests.length;
 // This checks ownership/context, not overload. Respect the eight-slot pilot;
 // saturation and immediate 503 rejection have dedicated deterministic tests.
 for(let offset=0;offset<24;offset+=8)await Promise.all(Array.from({length:8},async(_,localIndex)=>{
  const index=offset+localIndex;
  const actor=clients[index%clients.length];
  const tickets=await api('GET','/tickets?clientId='+actor.id,undefined,200,actor);
  assert.ok(tickets.every(t=>t.clientId===String(actor.id)),'Concurrent ownership isolation');
 }));
 const concurrent=report.requests.slice(concurrentStart);
 assert.equal(concurrent.length,24);
 assert.equal(new Set(concurrent.map(r=>r.correlationId)).size,24);
 report.concurrencyEvidence={requests:24,maxInFlight:8,distinctCorrelations:24,actors:clients.length};
 assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.compras;`),'3');
 assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.pagos;`),'3');
 assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.tickets;`),'3');
 assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.ticket_batches;`),'2');
 assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.reintegros;`),'2');
 const charges=sql(`SELECT datos FROM ${meta.schemas.compras}.operaciones_simuladas WHERE clave LIKE 'cobro:%' ORDER BY clave;`).split('\n').map(JSON.parse);
 assert.equal(charges.length,3);assert.equal(charges.filter(c=>c.aprobado).length,2);assert.equal(charges.filter(c=>!c.aprobado).length,1);
 assert.equal(charges.filter(c=>c.aprobado).reduce((sum,c)=>sum+Number(c.importe),0),45000);
 const refunds=JSON.parse(sql(`SELECT datos FROM ${meta.schemas.compras}.operaciones_simuladas WHERE clave LIKE 'reintegros-pago:%';`)).operaciones;
 assert.equal(Object.keys(refunds).length,2);assert.equal(Object.values(refunds).reduce((sum,r)=>sum+Number(r.importe),0),30000);
 report.financialEvidence={chargeAttempts:3,approvedCharges:2,rejectedCharges:1,approvedAmount:45000,refundOperations:2,refundedAmount:30000,tickets:3,emissionBatches:2};
 if(cache)report.cache=await require('./cache-smoke.cjs')({api,clients,admin,meta});
 if(recommendations)report.recommendations=await require('./recommendations-smoke.cjs')({meta,sql,attest,api,clients,organizers,admin,access,event,scenarios:report.scenarios,controls:recommendations});
 if(reportChecks)report.reports=await reportChecks.final(report.scenarios);
 if(notifications)report.notifications=await require('./notifications-smoke.cjs')({meta,sql,attest,api,clients,organizers,admin,access,event,scenarios:report.scenarios,controls:recommendations});
 if(queue)report.queue=await require('./queue-smoke.cjs')({meta,sql,attest,api,clients,organizers,admin,event});
 if(faults)report.faults=await require('./fault-campaign.cjs')({meta,sql,attest,api,clients,admin,scenarios:report.scenarios,controls:faults});
 if(prepareLoad)prepareLoad({meta,sql,attest,api,clients,organizers,admin,access,event,requests:report.requests});
 if(finalValidation)await finalValidation({api,clients,organizers,admin,access,scenarios:report.scenarios,meta,sql});
 return {...report,result:'PASS',identities:{clients:clients.map(c=>c.id),organizers:organizers.map(c=>c.id),admin:admin.id,access:access.id}};
};
