'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const verify=require('./verify-tracing.cjs');
function fixture(){
 const logs=Object.fromEntries(['gateway','identidad','eventos','inventario','compras','entradas'].map(n=>[n,[]]));
 const requests=[];let span=1;
 const operations=[['PATCH','/api/v1/events/1/publish',['gateway','eventos','inventario','identidad']],['POST','/compras',['gateway','compras','eventos','inventario']],['POST','/pagos',['gateway','compras','inventario','entradas']]];
 for(let n=0;n<operations.length;n++){
  const [method,path,components]=operations[n],correlationId=`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
  requests.push({method,path,status:200,correlationId});
  for(const component of components)logs[component].push({eventType:'HTTP_REQUEST',trace:{id:String(n+1).repeat(32)},span:{id:(span++).toString(16).padStart(16,'0')},correlationId,httpRoute:component==='identidad'?'/oauth2/token':path,httpStatus:200,actorType:'SERVICE'});
 }
 logs.inventario.push({eventType:'SCHEDULED_TASK',trace:{id:'a'.repeat(32)},span:{id:'a'.repeat(16)}});
 return {logs,requests,secrets:['sentinel-41-secret']};
}
function run(f){return verify({...f,logs:Object.fromEntries(Object.entries(f.logs).map(([k,rows])=>[k,rows.map(r=>typeof r==='string'?r:JSON.stringify(r)).join('\n')]))});}
test('accepts verified distributed circuits and independent scheduler',()=>assert.equal(run(fixture()).result,'PASS'));
test('rejects missing trace IDs',()=>{const f=fixture();delete f.logs.compras[0].trace;assert.throws(()=>run(f));});
test('rejects changed trace between services',()=>{const f=fixture();f.logs.compras[0].trace.id='b'.repeat(32);assert.throws(()=>run(f));});
test('rejects reused server span',()=>{const f=fixture();f.logs.eventos[0].span.id=f.logs.gateway[0].span.id;assert.throws(()=>run(f));});
test('rejects missing ingress evidence',()=>{const f=fixture();f.logs.gateway.shift();assert.throws(()=>run(f));});
test('rejects missing OAuth evidence',()=>{const f=fixture();f.logs.identidad[0].actorType='ANONYMOUS';assert.throws(()=>run(f));});
test('rejects scheduler inheriting an HTTP trace',()=>{const f=fixture();f.logs.inventario.at(-1).trace.id=f.logs.gateway[0].trace.id;assert.throws(()=>run(f));});
test('rejects sensitive query in logged path',()=>{const f=fixture();f.logs.entradas[0].httpRoute+='?secret=value';assert.throws(()=>run(f));});
test('rejects secrets even outside access records',()=>{const f=fixture();f.logs.identidad.push('sentinel-41-secret');assert.throws(()=>run(f));});
test('rejects JWT in any log line',()=>{const f=fixture();f.logs.identidad.push('eyJhbGciOiJSUzI1NiJ9.payload.signature');assert.throws(()=>run(f));});

test('scheduler root may have internal HTTP children without inheriting a public trace',()=>{const f=fixture();f.logs.compras.push({eventType:'HTTP_REQUEST',trace:{id:'a'.repeat(32)},span:{id:'b'.repeat(16)},correlationId:'00000000-0000-4000-8000-000000000099',httpRoute:'/internal/notifications/events',httpStatus:200,actorType:'SERVICE'});assert.equal(run(f).result,'PASS');});
