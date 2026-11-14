'use strict';
const assert=require('node:assert/strict');
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

function collect(logs,secrets=[]){
 const rows=[],http=[];
 for(const [component,text]of Object.entries(logs)){
  for(const secret of secrets.filter(Boolean))assert.ok(!text.includes(secret),'Secret in audit source');
  assert.ok(!/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(text),'JWT in audit source');
  for(const line of text.split('\n'))if(line.startsWith('{')){
   const row=JSON.parse(line);if(row.eventType==='AUDIT')rows.push({...row,component});
   if(row.eventType==='HTTP_REQUEST')http.push({...row,component});
  }
 }
 return {rows,http};
}
function contract({rows,http}){
 const ids=new Set();
 assert.ok(rows.length>0,'No audit evidence');
 for(const row of rows){
  assert.match(row.eventId,UUID);assert.ok(!ids.has(row.eventId),'Repeated eventId');ids.add(row.eventId);
  assert.ok(Number.isFinite(Date.parse(row.timestamp)));assert.ok(row.service?.name);
  assert.equal(row.message,'Audit event');assert.match(row.action,/^[A-Z_]+$/);
  assert.ok(['ATTEMPT','LOCAL_TRANSACTION','ORCHESTRATION'].includes(row.auditScope));
  assert.ok(['HTTP','EVENT','RESERVATION','PURCHASE','PAYMENT','RETURN','REFUND','TICKET','USER','TECHNICAL_CLIENT'].includes(row.resourceType));
  if(row.errorCode)assert.ok(['HTTP_REJECTED','HTTP_FAILED','TX_ROLLBACK','TX_OUTCOME_UNKNOWN','NO_LOCAL_TRANSACTION','AUTHENTICATION_REJECTED','PROVISION_REJECTED'].includes(row.errorCode));
  assert.ok(['CLIENTE','ORGANIZADOR','ADMINISTRADOR','PERSONAL_ACCESO','SERVICIO','SISTEMA'].includes(row.actorType));
  assert.equal(typeof row.actorVerified,'boolean');
  assert.ok(['SUCCESS','REJECTED','FAILED','PENDING'].includes(row.outcome));
  if(!row.actorVerified||row.actorType==='SISTEMA')assert.equal(row.actorId,undefined);
  if(row.actorId!==undefined)assert.equal(row.actorVerified,true);
  if(row.operationId){assert.match(row.operationId,/^(EVENT|RESERVATION|PURCHASE|PAYMENT|RETURN|REFUND|TICKET|USER|TECHNICAL_CLIENT):/);assert.notEqual(row.operationId,row.correlationId);assert.notEqual(row.operationId,row.trace?.id);}
  if(row.correlationId){
   assert.match(row.correlationId,UUID);assert.match(row.trace?.id||'',/^[0-9a-f]{32}$/);
   const request=http.find(r=>r.component===row.component&&r.correlationId===row.correlationId);
   assert.ok(request,'Audit without matching HTTP evidence');assert.equal(row.trace.id,request.trace.id);
   if(row.action==='ACCESS_ATTEMPT'&&request.actorType==='ANONYMOUS'){assert.equal(row.actorVerified,false);assert.equal(row.actorType,'SISTEMA');}
  }
  if(row.action==='ACCESS_ATTEMPT'){
   assert.ok(['REJECTED','FAILED'].includes(row.outcome));assert.equal(row.resourceId,undefined);assert.equal(row.operationId,undefined);
   assert.ok(['HTTP_REJECTED','HTTP_FAILED'].includes(row.errorCode));
  }else if(row.outcome==='SUCCESS'){assert.ok(row.resourceId);assert.ok(row.operationId);}
  for(const key of ['Authorization','password','client_secret','cookie','body','claveIdempotencia','token'])assert.equal(row[key],undefined);
 }
 return true;
}
function verify({logs,secrets,commercial,meta,sql,progress=()=>{}}){
 progress('contract');const data=collect(logs,secrets);contract(data);const {rows}=data;
 progress('required-actions');
 for(const action of ['EVENT_PUBLICATION','EVENT_CANCELLATION','EVENT_CANCELLATION_RETRY','RESERVATION_CREATED','RESERVATION_CONFIRMED','RESERVATION_RELEASED','PURCHASE_CREATED','PAYMENT_STATE','PAYMENT_PROCESS','PAYMENT_RECOVERY','PAYMENT_REPLAY','RETURN_REQUESTED','RETURN_DECIDED','REFUND_STATE','RETURN_PROCESS','REFUND_REPLAY','TICKETS_ISSUED','TICKETS_ANNULLED','LOGIN','PRIVILEGED_PROVISION','TECHNICAL_CLIENT_PROVISION','ACCESS_ATTEMPT'])
  assert.ok(rows.some(r=>r.action===action),'Missing audited operation '+action);
 progress('login-results');
 assert.ok(rows.some(r=>r.action==='LOGIN'&&r.outcome==='REJECTED'&&!r.actorVerified&&r.errorCode==='AUTHENTICATION_REJECTED'));
 assert.ok(rows.some(r=>r.action==='LOGIN'&&r.outcome==='SUCCESS'&&r.actorType==='CLIENTE'));
 assert.ok(!rows.some(r=>r.errorCode==='NO_LOCAL_TRANSACTION'),'Production local action outside a transaction');
 progress('financial-transitions');const matching=(action,state)=>rows.filter(r=>r.action===action&&r.resourceState===state);
 const approved=matching('PAYMENT_STATE','APROBADO');assert.equal(approved.length,2);assert.equal(new Set(approved.map(r=>r.resourceId)).size,2);
 assert.equal(matching('PAYMENT_STATE','RECHAZADO').length,1);
 assert.ok(matching('PAYMENT_STATE','RESULTADO_DESCONOCIDO').some(r=>r.outcome==='PENDING'));
 const refunds=matching('REFUND_STATE','COMPLETADO');assert.equal(refunds.length,2);assert.equal(new Set(refunds.map(r=>r.resourceId)).size,2);
 assert.equal(rows.filter(r=>r.action==='TICKETS_ISSUED').length,2);
 progress('persisted-states');for(const r of refunds){assert.match(r.resourceId,/^[1-9][0-9]*$/);assert.equal(sql(`SELECT estado FROM ${meta.schemas.compras}.reintegros WHERE id=${r.resourceId};`),'COMPLETADO');}
 for(const scenario of commercial.scenarios){
  for(const id of [scenario.reservation,scenario.purchase,scenario.event])assert.ok(Number.isSafeInteger(id)&&id>0);
  const reservation=rows.filter(r=>r.resourceType==='RESERVATION'&&r.resourceId===String(scenario.reservation));
  assert.ok(reservation.some(r=>r.action==='RESERVATION_CREATED'&&r.actorType==='CLIENTE'&&r.actorId===String(scenario.client)));
  assert.ok(rows.some(r=>r.action==='PURCHASE_CREATED'&&r.resourceId===String(scenario.purchase)&&r.actorId===String(scenario.client)));
  const expected=scenario.name==='rechazo_y_liberacion'?'LIBERADA':'CONFIRMADA';
  assert.equal(sql(`SELECT estado FROM ${meta.schemas.inventario}.reserva WHERE id_reserva=${scenario.reservation};`),expected);
  assert.ok(reservation.some(r=>r.resourceState===expected&&r.outcome==='SUCCESS'&&r.actorType==='SERVICIO'));
 }
 progress('original-administrator');const numbered=commercial.scenarios.find(s=>s.name==='numerada_pago_idempotente_devolucion_parcial_cancelacion');
 const cancellation=rows.filter(r=>['EVENT_CANCELLATION','EVENT_CANCELLATION_RETRY'].includes(r.action)&&r.resourceId===String(numbered.event));
 assert.ok(cancellation.some(r=>r.action==='EVENT_CANCELLATION'&&r.outcome==='SUCCESS'));
 assert.ok(cancellation.some(r=>r.action==='EVENT_CANCELLATION_RETRY'&&r.outcome==='SUCCESS'));
 assert.ok(cancellation.every(r=>r.initiatorId===commercial.identities.admin&&r.initiatorType==='ADMINISTRADOR'));
 assert.equal(sql(`SELECT cancellation_authorized_admin_id FROM ${meta.schemas.eventos}.event WHERE event_id=${numbered.event};`),String(commercial.identities.admin));
 for(const r of rows.filter(r=>r.action==='EVENT_PUBLICATION_LOCAL'||r.action==='EVENT_CANCELLATION_LOCAL'))assert.equal(r.outcome,'PENDING');
 const completedReturns=rows.filter(r=>r.action==='RETURN_PROCESS'&&r.outcome==='SUCCESS');
 assert.equal(new Set(completedReturns.map(r=>r.resourceId)).size,2);
 for(const r of completedReturns)assert.equal(sql(`SELECT estado FROM ${meta.schemas.compras}.devoluciones WHERE id=${r.resourceId};`),'COMPLETADA');
 const outcomes=Object.fromEntries(['SUCCESS','REJECTED','FAILED','PENDING'].map(o=>[o,rows.filter(r=>r.outcome===o).length]));
 progress('complete');
 return {result:'PASS',events:rows.length,outcomes,actions:[...new Set(rows.map(r=>r.action))].sort(),privilegedProvisions:rows.filter(r=>r.action==='PRIVILEGED_PROVISION'&&r.outcome==='SUCCESS').length,technicalClientProvisions:rows.filter(r=>r.action==='TECHNICAL_CLIENT_PROVISION'&&r.outcome==='SUCCESS').length,approvedPaymentTransitions:2,completedRefundTransitions:2,emissionBatches:2,originalAdministratorPreserved:true,persistedStatesVerified:true,financialEvidence:commercial.financialEvidence};
}
module.exports={collect,contract,verify};
