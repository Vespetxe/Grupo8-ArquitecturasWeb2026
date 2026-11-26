'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),http=require('node:http'),fs=require('node:fs'),path=require('node:path');
test('real Newman accepts Node URL input, sends one POST and exports no credentials',async()=>{
 const directory=fs.mkdtempSync(path.resolve(__dirname,'../target/openfeign/newman-unit-'));let calls=0;
 const server=http.createServer((request,response)=>{calls++;assert.equal(request.method,'POST');assert.equal(request.headers.authorization,'Bearer sentinel-only-test');let text='';request.on('data',chunk=>text+=chunk);request.on('end',()=>{assert.equal(JSON.parse(text).password,'secret-only-test');response.writeHead(201,{'Content-Type':'application/json','X-Correlation-Id':'00000000-0000-4000-8000-000000000001'});response.end('{"id":7}');});});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{const transport=require('./newman-transport.cjs').create({directory,runId:'offline-newman'});const r=await transport.request({method:'POST',url:new URL('http://127.0.0.1:'+server.address().port+'/fixture'),body:{password:'secret-only-test'},headers:{Authorization:'Bearer sentinel-only-test'},expected:201});assert.equal(r.body.id,7);assert.equal(calls,1);const report=transport.finish();assert.equal(report.requests,1);assert.equal(report.assertions,2);const saved=fs.readFileSync(path.join(directory,'newman-executed.postman_collection.json'),'utf8');assert(!saved.includes('sentinel-only-test'));assert(!saved.includes('secret-only-test'));}finally{await new Promise(resolve=>server.close(resolve));}
});
test('lost POST response is not retried by the Newman transport',async()=>{
 const directory=fs.mkdtempSync(path.resolve(__dirname,'../target/openfeign/newman-lost-'));let calls=0;
 const server=http.createServer((request,response)=>{calls++;request.resume();request.on('end',()=>response.destroy());});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{const transport=require('./newman-transport.cjs').create({directory,runId:'lost-response'});await assert.rejects(transport.request({method:'POST',url:new URL('http://127.0.0.1:'+server.address().port+'/fixture'),body:{originalId:1},expected:201}),/no HTTP response/);assert.equal(calls,1);}finally{await new Promise(resolve=>server.close(resolve));}
});
