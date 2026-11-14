'use strict';
const assert=require('node:assert/strict');
const delay=ms=>new Promise(r=>setTimeout(r,ms));
module.exports=async function({docker,container,logs,secrets,requests}){
 function query(...args){return docker(['exec',container,'python3','/collector/archive.py',...args]).trim().split('\n').filter(Boolean).map(JSON.parse);}
 const source=Object.fromEntries(Object.entries(logs).map(([name,text])=>[name,text.split('\n').filter(l=>l.startsWith('{')).map(JSON.parse)]));
 const auditIds=Object.values(source).flat().filter(r=>r.eventType==='AUDIT').map(r=>r.eventId);
 let rows=[];const deadline=Date.now()+60000;
 while(Date.now()<deadline){rows=query('--all','--limit','100000');const ids=new Set(rows.map(r=>r.eventId));if(auditIds.every(id=>ids.has(id))&&requests.every(r=>rows.some(v=>v.correlationId===r.correlationId&&v.service.name===source.gateway[0].service.name)))break;await delay(1000);}
 const text=rows.map(r=>JSON.stringify(r)).join('\n');
 for(const secret of secrets.filter(Boolean))assert.ok(!text.includes(secret),'Secret in central archive');
 assert.ok(!/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(text));
 const centralAudit=rows.filter(r=>r.eventType==='AUDIT'),ids=centralAudit.map(r=>r.eventId);
 assert.equal(new Set(ids).size,ids.length,'No transport duplication of audit events');
 for(const id of auditIds)assert.ok(ids.includes(id),'Missing source audit event');
 const reconstructed={};
 for(const [name,records]of Object.entries(source)){
  const service=records.find(r=>r.service?.name)?.service.name;assert.ok(service);
  const received=rows.filter(r=>r.service?.name===service);assert.ok(received.length>0,'Missing service '+name);
  reconstructed[name]=received.map(r=>JSON.stringify(r)).join('\n');
 }
 const tracing=require('./verify-tracing.cjs')({logs:reconstructed,requests,secrets});
 const cid=requests.find(r=>r.correlationId).correlationId;
 const correlated=query('--query','correlationId','--value',cid);assert.ok(correlated.length>0&&correlated.every(r=>r.correlationId===cid));
 const traceId=correlated.find(r=>r.trace?.id).trace.id;
 const traced=query('--query','traceId','--value',traceId);assert.ok(traced.length>0&&traced.every(r=>r.trace.id===traceId));
 const operationId=centralAudit.find(r=>r.operationId).operationId;
 const operations=query('--query','operationId','--value',operationId);assert.ok(operations.length>0&&operations.every(r=>r.operationId===operationId));
 const stats=JSON.parse(docker(['exec',container,'python3','-c',"import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:9881/health').read().decode())"]));
 assert.ok(stats.bytes>0&&stats.bytes<=stats.cap);
 return {result:'PASS',records:rows.length,services:Object.keys(reconstructed).length,auditEvents:ids.length,allSourceAuditEventsPresent:true,queryCorrelation:correlated.length,queryTrace:traced.length,queryOperation:operations.length,sourceAuditCount:auditIds.length,tracing,startWithoutCollector:true,commercialDuringCollectorOutage:true,recoveredWithoutApplicationRestart:true,stats};
};
