'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
// Real Newman execution, without stdout reporters or exported credentials.
function create({directory,runId}){
 const modulePath=process.env.TY_NEWMAN_MODULE||path.resolve(__dirname,'../target/openfeign/newman/node_modules/newman');
 const newman=require(modulePath);
 const items=[],results=[],secrets=new Map();let sequence=0,assertions=0;
 const secret=value=>{if(!secrets.has(value))secrets.set(value,'private_'+secrets.size);return '{{'+secrets.get(value)+'}}';};
 function safeBody(value){if(Array.isArray(value))return value.map(safeBody);if(value&&typeof value==='object')return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,/password|secret|token/i.test(k)?secret(String(v)):safeBody(v)]));return value;}
 async function request({method,url,body,headers={},expected=200}){
  url=String(url); // Node URL objects are not Postman SDK Url definitions.
  const index=sequence++,allowed=Array.isArray(expected)?expected:[expected];
  const rawHeaders=Object.entries(headers).map(([key,value])=>({key,value:String(value)}));
  const live={name:method+' '+new URL(url).pathname,request:{method,url,header:rawHeaders,...(body===undefined?{}:{body:{mode:'raw',raw:JSON.stringify(body),options:{raw:{language:'json'}}}})},event:[{listen:'test',script:{type:'text/javascript',exec:[`pm.test('HTTP conforme al contrato',()=>pm.expect(${JSON.stringify(allowed)}).to.include(pm.response.code));`,`pm.test('Respuesta JSON válida',()=>{if(pm.response.text()) JSON.parse(pm.response.text());});`]}}]};
  if(body!==undefined)live.request.header.push({key:'Content-Type',value:'application/json'});
  const collection={info:{name:'TicketYa '+runId,schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},item:[live]};
  const response=await new Promise((resolve,reject)=>newman.run({collection,reporters:[],ignoreRedirects:true,timeoutRequest:25000,timeoutScript:5000},(error,summary)=>{
   if(error)return reject(new Error('Newman transport failed: '+error.name));
   const execution=summary.run.executions[0];if(!execution?.response)return reject(new Error('Newman received no HTTP response: '+(execution?.requestError?.code||summary.run.failures[0]?.error?.name||'EMPTY_EXECUTION')));
   assertions+=summary.run.stats.assertions.total;
   if(summary.run.failures.length)return reject(new Error(method+' '+new URL(url).pathname+': Newman HTTP assertion failed ('+execution.response.code+')'));
   const r=execution.response;resolve({status:r.code,correlationId:r.headers.get('X-Correlation-Id'),body:r.text()?JSON.parse(r.text()):null});
  }));
  const saved=structuredClone(live);saved.request.url='{{baseUrl}}'+new URL(url).pathname+new URL(url).search;
  saved.request.header=saved.request.header.map(h=>h.key.toLowerCase()==='authorization'?{key:h.key,value:secret(h.value)}:h);
  if(body!==undefined)saved.request.body.raw=JSON.stringify(safeBody(body),null,2);
  items[index]=saved;results[index]={method,path:new URL(url).pathname,status:response.status,expected:allowed,correlationId:response.correlationId,assertions:2};
  fs.writeFileSync(path.join(directory,"newman-progress.json"),JSON.stringify({runner:"Newman",requests:results.length,assertions,results},null,2));
  return response;
 }
 function finish(){
  assert(items.every(Boolean));const collection={info:{name:'TicketYa — campaña real OpenFeign/Newman',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',description:'Captura de campaña aislada. Sin credenciales. Usar el runner para preparar nuevas identidades/datos; no repetir escrituras sobre una demo histórica.'},variable:[{key:'baseUrl',value:''},...[...secrets.values()].map(key=>({key,value:''}))],item:[{name:'Flujos ejecutados con JWT real',item:items}]};
  fs.writeFileSync(path.join(directory,'newman-executed.postman_collection.json'),JSON.stringify(collection,null,2));
  const report={runner:'Newman '+require(path.join(modulePath,'package.json')).version,requests:results.length,assertions,failures:0,results};
  fs.writeFileSync(path.join(directory,'newman-results.json'),JSON.stringify(report,null,2));return report;
 }
 return {request,finish};
}
module.exports={create};
