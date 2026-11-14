'use strict';
const assert=require('node:assert/strict');
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function verifySaturation(responses,entries){
 const rejected=responses.filter(r=>r.status===503),accepted=responses.filter(r=>r.status===200);
 assert.ok(rejected.length>0&&accepted.length>0,'Observe both saturation and successful reads');
 for(const r of rejected){
  assert.equal(r.body.code,'ORDERS_UNAVAILABLE');
  assert.ok(entries.some(e=>e.eventType==='DEPENDENCY_READ'&&e.errorCategory==='BULKHEAD_FULL'&&e.correlationId===r.correlationId),'503 must match a bulkhead rejection, not an arbitrary failure');
 }
 return {concurrency:responses.length,accepted:accepted.length,technicalRejections:rejected.length};
}
function unchanged(before,after){assert.deepEqual(after,before,'No financial effects, lost identifiers or compensations during faults');}
module.exports=async function campaign({meta,sql,attest,api,clients,admin,scenarios,controls}){
 const result={};let stopped,paused=false;
 const first=scenarios[0],recovered=scenarios[2],route='/tickets?orderId='+first.purchase;
 function snapshot(){
  const c=meta.schemas.compras,t=meta.schemas.entradas;
  return [
   `SELECT id,reserva_id,estado,etapa FROM ${c}.compras ORDER BY id;`,
   `SELECT id,compra_id,estado,importe,importe_reintegrado,referencia_externa FROM ${c}.pagos ORDER BY id;`,
   `SELECT id,pago_id,devolucion_id,estado,importe FROM ${c}.reintegros ORDER BY id;`,
   `SELECT clave,datos FROM ${c}.operaciones_simuladas ORDER BY clave;`,
   `SELECT ticket_id,batch_id,event_id,client_id,status FROM ${t}.tickets ORDER BY ticket_id;`,
   `SELECT order_id FROM ${t}.ticket_batches ORDER BY order_id;`,
   `SELECT id_reserva,estado FROM ${meta.schemas.inventario}.reserva ORDER BY id_reserva;`,
   `SELECT event_id,status,cancellation_authorized_admin_id,cancellation_authorization_jti,cancellation_authorized_at FROM ${meta.schemas.eventos}.event ORDER BY event_id;`
  ].map(sql);
 }
 const before=snapshot();
 const read=(expected=200)=>api('GET',route,undefined,expected,clients[0],{},false);
 try{
  // A best-effort driver cannot retain its queue across application shutdown.
  // Finish the collector-outage check before stopping applications.
  await controls.beforeFaults();
  attest();controls.pausePurchases();paused=true;
  // Hold eight real dependency reads, then overload the semaphore without changing its limits.
  const responses=Array.from({length:24},()=>read([200,503]));
  await sleep(350);controls.resumePurchases();paused=false;
  const burst=await Promise.all(responses);attest();
  result.saturation=verifySaturation(burst,controls.entries());
  for(const r of burst.filter(r=>r.status===200)){assert.equal(r.body.length,2);assert.ok(r.body.every(t=>t.orderId===String(first.purchase)));}
  await read();unchanged(before,snapshot());result.saturation.recovered=true;

  controls.stop('compras');stopped='compras';
  for(let i=0;i<12;i++)await read(503);
  const openedAt=Date.now();
  assert.ok(controls.entries().some(e=>e.eventType==='DEPENDENCY_READ'&&e.errorCategory==='CIRCUIT_OPEN'),'OPEN rejects dispatch');
  unchanged(before,snapshot());
  await controls.start(stopped);stopped=undefined;
  await sleep(Math.max(0,11000-(Date.now()-openedAt)));
  await read();await read();
  const transitions=controls.entries().filter(e=>e.eventType==='CIRCUIT_TRANSITION').map(e=>e.circuitState);
  const open=transitions.lastIndexOf('OPEN');
  assert.deepEqual(transitions.slice(open),['OPEN','HALF_OPEN','CLOSED']);
  unchanged(before,snapshot());result.dependency={failedReads:12,transitions:transitions.slice(open),noDispatchWhileOpen:true,recovered:true};

  controls.stop('identidad');stopped='identidad';
  await read();await read();unchanged(before,snapshot());
  await controls.start(stopped);stopped=undefined;
  result.oauth={cachedTechnicalTokenDuringOutage:true,recovered:true};

  attest();
  const payments=await Promise.all(Array.from({length:8},()=>api('POST','/pagos',{compraId:recovered.purchase,claveIdempotencia:`simular-timeout-${meta.id}`,medioPago:'TARJETA_DEBITO'},200,clients[2],{},false)));
  assert.ok(payments.every(p=>p.id===recovered.payment&&p.estado==='APROBADO'));
  const refunds=await Promise.all(Array.from({length:8},()=>api('POST',`/admin/devoluciones/${first.refund}/procesar`,{claveIdempotencia:`reintegro-${meta.id}`,importe:15000},200,admin,{},false)));
  assert.ok(refunds.every(r=>r.etapa==='FINALIZADA'&&r.reintegro.estado==='COMPLETADO'&&r.reintegro.importe===15000));
  attest();unchanged(before,snapshot());
  result.criticalConcurrency={paymentReplays:8,refundReplays:8,samePaymentId:true,sameRefundId:new Set(refunds.map(r=>r.reintegro.id)).size===1,financialSnapshotUnchanged:true};
  assert.equal(result.criticalConcurrency.sameRefundId,true);
  return {...result,result:'PASS',originalAuthorityAndIdentifiersPreserved:true};
 }finally{
  if(paused&&controls.available())controls.resumePurchases();
  if(stopped&&controls.available())await controls.start(stopped);
 }
};
module.exports.verifySaturation=verifySaturation;
module.exports.unchanged=unchanged;
