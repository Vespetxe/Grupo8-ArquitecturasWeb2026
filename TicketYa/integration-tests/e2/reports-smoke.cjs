'use strict';
const assert=require('node:assert/strict');
module.exports=function({meta,sql,api,clients,organizers,admin,access,controls}){
 const snapshots=[];let checkedSecurity=false;
 const path=event=>'/api/v1/events/'+event+'/report';
 const get=(event,actor=organizers()[0],expected=200)=>api('GET',path(event),undefined,expected,actor);
 return {
 async check(event,expected){
  const report=await get(event.event);assert.equal(report.eventId,event.event);assert.equal(report.sectors.length,1);const totals=report.totals;
  assert.equal(totals.soldTickets,expected.sold);assert.equal(totals.returnedTickets,expected.returned);assert.equal(totals.netSoldTickets,expected.sold-expected.returned);assert.equal(totals.grossCollected,expected.gross);assert.equal(totals.refundedAmount,expected.refund);assert.equal(totals.netCollected,expected.net);assert.equal(totals.capacity,2);assert.equal(totals.occupancyPercent,expected.occupancy);assert.equal(report.financialComplete,expected.complete!==false);assert.equal(report.sectors[0].sectorId,event.sector);assert.deepEqual(report.sectors[0].metrics,totals);
  const repeated=await get(event.event);assert.deepEqual(repeated.totals,totals);snapshots.push({event:event.event,...expected});
  if(!checkedSecurity){checkedSecurity=true;await get(event.event,null,401);for(const actor of [clients()[0],admin(),access()])await get(event.event,actor,403);await get(event.event,organizers()[1],404);await get(999999999,organizers()[0],404);}
 },
 async final(scenarios){
  const event=scenarios.find(s=>s.name==='pago_incierto_recuperado').event;
  const before=[sql(`SELECT id,estado,importe,importe_reintegrado FROM ${meta.schemas.compras}.pagos ORDER BY id;`),sql(`SELECT clave,datos FROM ${meta.schemas.compras}.operaciones_simuladas ORDER BY clave;`),sql(`SELECT ticket_id,status FROM ${meta.schemas.entradas}.tickets ORDER BY ticket_id;`)];
  const settled=await get(event);let stopped=false;
  try{controls.stop('compras');stopped=true;await get(event,organizers()[0],503);await controls.start('compras');stopped=false;const recovered=await get(event);assert.deepEqual(recovered.totals,settled.totals);}finally{if(stopped&&controls.available())await controls.start('compras');}
  const after=[sql(`SELECT id,estado,importe,importe_reintegrado FROM ${meta.schemas.compras}.pagos ORDER BY id;`),sql(`SELECT clave,datos FROM ${meta.schemas.compras}.operaciones_simuladas ORDER BY clave;`),sql(`SELECT ticket_id,status FROM ${meta.schemas.entradas}.tickets ORDER BY ticket_id;`)];assert.deepEqual(after,before);
  return {result:'PASS',snapshots,ownedEvents:true,readScopesRealOAuth:true,dependencyFailure503:true,recovery:true,financialSnapshotUnchanged:true};
 }
 };
};
