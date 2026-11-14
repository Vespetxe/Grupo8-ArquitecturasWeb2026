'use strict';
const assert=require('node:assert/strict');
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

// Takes logs in memory; writes only counts and non-secret operation metadata.
module.exports=function verify({logs,requests,secrets=[]}){
 const records=[];
 for(const [service,text] of Object.entries(logs)){
  for(const secret of secrets.filter(Boolean))assert.ok(!text.includes(secret),'Secret found in '+service+' logs');
  assert.ok(!/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(text),'JWT found in logs');
  for(const line of text.split('\n')){
   if(!line.startsWith('{'))continue;
   const row=JSON.parse(line);
   if(row.eventType!=='HTTP_REQUEST'&&row.eventType!=='SCHEDULED_TASK')continue;
   assert.match(row.trace?.id||'',/^[0-9a-f]{32}$/,'Missing trace in '+service);
   assert.match(row.span?.id||'',/^[0-9a-f]{16}$/,'Missing span in '+service);
   if(row.eventType==='HTTP_REQUEST'){
    assert.match(row.correlationId,UUID);assert.ok(!row.httpRoute.includes('?'));
    assert.ok(['ANONYMOUS','USER','SERVICE'].includes(row.actorType));
   }
   records.push({...row,component:service});
  }
 }
 for(const name of ['gateway','identidad','eventos','inventario','compras','entradas'])
  assert.ok(records.some(r=>r.component===name&&r.eventType==='HTTP_REQUEST'),'No HTTP trace for '+name);
 const distributed=[],ingressTraces=new Set();
 for(const request of requests){
  assert.match(request.correlationId,UUID);
  const rows=records.filter(r=>r.eventType==='HTTP_REQUEST'&&r.correlationId===request.correlationId);
  const ingress=rows.filter(r=>r.component==='gateway');
  assert.equal(ingress.length,1,'One ingress record per HTTP request');
  assert.equal(ingress[0].httpStatus,request.status);
  assert.ok(!ingressTraces.has(ingress[0].trace.id),'Fresh trace for independent public operation');
  ingressTraces.add(ingress[0].trace.id);
  assert.equal(new Set(rows.map(r=>r.trace.id)).size,1,'Same distributed trace');
  assert.equal(new Set(rows.map(r=>r.span.id)).size,rows.length,'Different server spans at every hop');
  const components=[...new Set(rows.map(r=>r.component))];
  if(components.length>1)distributed.push({method:request.method,path:request.path.split('?')[0],status:request.status,components,correlationId:request.correlationId,traceId:ingress[0].trace.id});
 }
 for(const [method,path,required] of [
  ['PATCH',/\/publish$/,['gateway','eventos','inventario']],
  ['POST',/^\/compras$/,['gateway','compras','eventos','inventario']],
  ['POST',/^\/pagos$/,['gateway','compras','inventario','entradas']]])
  assert.ok(distributed.some(r=>r.method===method&&path.test(r.path)&&r.status<300&&required.every(s=>r.components.includes(s))),'Missing distributed circuit '+path);
 assert.ok(records.some(r=>r.component==='identidad'&&r.httpRoute==='/oauth2/token'&&r.httpStatus===200&&r.actorType==='SERVICE'),'OAuth2 tracing');
 const timers=records.filter(r=>r.eventType==='SCHEDULED_TASK');
 assert.ok(timers.length>0,'Scheduler tracing');
 const httpTraces=new Set(records.filter(r=>r.eventType==='HTTP_REQUEST').map(r=>r.trace.id));
 assert.ok(timers.every(r=>!httpTraces.has(r.trace.id)),'Scheduler root independent of HTTP');
 return {result:'PASS',httpRecords:records.filter(r=>r.eventType==='HTTP_REQUEST').length,scheduledRecords:timers.length,distributedRequests:distributed.length,circuits:distributed.filter(r=>r.method!=='GET')};
};
