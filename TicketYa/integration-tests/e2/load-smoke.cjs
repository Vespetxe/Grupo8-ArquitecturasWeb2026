'use strict';
const assert=require('node:assert/strict');
function summarize(rows){
 const latencies=rows.map(r=>r.durationMs).sort((a,b)=>a-b);assert.ok(latencies.every(Number.isFinite));
 const percentile=p=>latencies.length?latencies[Math.ceil(latencies.length*p)-1]:null;
 const expected=r=>r.expected===undefined?[200,201,409,429,503].includes(r.status):Array.isArray(r.expected)?r.expected.includes(r.status):r.expected===r.status;
 return {requests:rows.length,successes:rows.filter(r=>r.status>=200&&r.status<300).length,expectedRejections:rows.filter(r=>r.status>=400&&expected(r)).length,conflicts409:rows.filter(r=>r.status===409).length,technicalRejections:rows.filter(r=>[429,503].includes(r.status)).length,unexpectedErrors:rows.filter(r=>!expected(r)).length,latencyMs:{p50:percentile(.5),p95:percentile(.95),max:latencies.at(-1)??null}};
}
module.exports=async function load({meta,sql,attest,api,clients,event,requests}){
 const start=requests.length;
 const e=await event(false,15000,'bounded-load');
 const before=[sql(`SELECT id,estado,importe,importe_reintegrado FROM ${meta.schemas.compras}.pagos ORDER BY id;`),sql(`SELECT id,estado,importe FROM ${meta.schemas.compras}.reintegros ORDER BY id;`)];
 attest();const reservations=await Promise.all(Array.from({length:12},(_,i)=>api('POST','/reservas',{idCliente:clients[i%clients.length].id,idEvento:e.event,idSector:e.sector,cantidad:1},[201,409,429,503],clients[i%clients.length],{},false)));attest();
 const winners=reservations.map((r,i)=>({...r,client:clients[i%clients.length]})).filter(r=>r.status===201);
 assert.equal(winners.length,2,'Exactly the event capacity must be reserved');
 for(const r of reservations.filter(r=>r.status===409))assert.equal(r.body.mensaje,'Capacidad o aforo insuficiente');
 const purchases=await Promise.all(winners.map(async r=>{
  const c=await api('POST','/compras',{clienteId:r.client.id,eventoId:e.event,reservaId:r.body.idReserva,detalles:[{sectorId:e.sector,cantidad:1}]},201,r.client,{},false);
  const key=`bounded-load-${meta.id}-${c.id}`;const pay=await api('POST','/pagos',{compraId:c.id,claveIdempotencia:key,medioPago:'TARJETA_DEBITO'},201,r.client,{},false);assert.equal(pay.estado,'APROBADO');
  return {client:r.client,purchase:c.id,payment:pay.id,key};
 }));
 const repeats=await Promise.all(purchases.flatMap(p=>Array.from({length:4},()=>api('POST','/pagos',{compraId:p.purchase,claveIdempotencia:p.key,medioPago:'TARJETA_DEBITO'},[200,409,429,503],p.client,{},false))));
 assert.ok(repeats.filter(r=>r.status===200).every(r=>purchases.some(p=>p.payment===r.body.id)));
 for(const p of purchases){const c=await api('GET','/compras/'+p.purchase,undefined,200,p.client);assert.equal(c.estado,'COMPLETADA');const ts=await api('GET','/tickets?orderId='+p.purchase,undefined,200,p.client);assert.equal(ts.length,1);assert.equal(ts[0].status,'EMITIDA');assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.pagos WHERE compra_id=${p.purchase};`),'1');assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.ticket_batches WHERE order_id='${p.purchase}';`),'1');}
 const stock=await api('GET',`/disponibilidad/eventos/${e.event}/sectores/${e.sector}`,undefined,200,clients[0]);assert.equal(stock.disponibles,0);
 assert.equal(sql(`SELECT COALESCE(SUM(cantidad),0) FROM ${meta.schemas.inventario}.reserva WHERE id_evento=${e.event} AND estado='CONFIRMADA';`),'2');
 assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.tickets WHERE event_id='${e.event}';`),'2');
 const paymentIds=purchases.map(p=>p.payment).join(',');
 assert.equal(sql(`SELECT id,estado,importe,importe_reintegrado FROM ${meta.schemas.compras}.pagos WHERE id NOT IN (${paymentIds}) ORDER BY id;`),before[0]);assert.equal(sql(`SELECT id,estado,importe FROM ${meta.schemas.compras}.reintegros ORDER BY id;`),before[1]);
 const rows=requests.slice(start);return {result:'PASS',concurrency:12,capacity:2,reservationRequests:12,reservationsAccepted:2,reservationConflicts:reservations.filter(r=>r.status===409).length,reservationTechnicalRejections:reservations.filter(r=>[429,503].includes(r.status)).length,paymentReplays:8,...summarize(rows),event:e.event,sector:e.sector,purchases:purchases.map(p=>({clientId:p.client.id,purchaseId:p.purchase,paymentId:p.payment})),approvedAmount:30000,emittedTickets:2,duplicatePayments:0,duplicateTickets:0,overselling:0,previousFinancialRecordsUnchanged:true};
};
module.exports.summarize=summarize;
