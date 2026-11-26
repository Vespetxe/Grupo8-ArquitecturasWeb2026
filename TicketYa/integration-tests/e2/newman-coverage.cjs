'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const contracts=require('./postman-contracts.cjs');
module.exports=function verify({meta,credentials,docker,clients,admin,scenarios,sql}){
 const items=[];
 const test=expected=>[{listen:'test',script:{type:'text/javascript',exec:[`pm.test('Resultado exacto del caso',()=>pm.expect(pm.response.code).to.eql(${expected}));`]}}];
 for(const row of contracts.inventory()){
  const route=row.route.replace(/\{([^}]+)\}/g,(_,name)=>name==='owner'?String(clients[0].id):name==='id'&&row.route.includes('/participants/')?'999999999': '1');
  const anonymousRead=row.public&&row.roles.length===0&&row.method==='GET';
  let expected=row.public?(anonymousRead?200:401):403;
  if(row.route==='/api/v1/participants/{id}'&&row.method==='GET')expected=404;
  if(row.route==='/auth/register'||row.route==='/auth/login')expected=400;
  const request={method:row.method,url:'http://'+(row.public?'gateway:8080':row.service+':'+({eventos:8081,inventario:8082,compras:8084,entradas:8083}[row.service]))+route,header:[{key:'Accept',value:'application/json'}]};
  if(!row.public)request.header.push({key:'Authorization',value:'Bearer {{humanToken}}'});
  if(['POST','PUT','PATCH'].includes(row.method)){request.header.push({key:'Content-Type',value:'application/json'});request.body={mode:'raw',raw:'{}'};}
  items.push({name:row.service+' '+row.method+' '+row.route,request,event:test(expected)});
 }
 const calls={eventos:['inventario','entradas','compras'],compras:['eventos','inventario','entradas'],entradas:['compras']};
 const scopes=require('./security-isolation.cjs').SCOPES;
 const values=[{key:'humanToken',value:clients[0].token},{key:'adminToken',value:admin.token}];
 for(const [caller,targets]of Object.entries(calls))for(const target of targets){
  const key=caller+'_'+target;values.push({key:'secret_'+key,value:credentials['TY_REG_'+key.toUpperCase()+'_SECRET']});
  items.push({name:'OAuth2 '+caller+' → '+target,request:{method:'POST',url:'http://identidad:8085/oauth2/token',auth:{type:'basic',basic:[{key:'username',value:caller+'-to-'+target,type:'string'},{key:'password',value:'{{secret_'+key+'}}',type:'string'}]},body:{mode:'urlencoded',urlencoded:[{key:'grant_type',value:'client_credentials'},{key:'scope',value:scopes[caller+':'+target]}]}},event:[...test(200),{listen:'test',script:{type:'text/javascript',exec:[`const token=pm.response.json();pm.test('Token técnico emitido',()=>pm.expect(token.access_token).to.be.a('string'));pm.collectionVariables.set('token_${key}',token.access_token);`]}}]});
 }
 for(const [service,key]of Object.entries({eventos:'compras_eventos',inventario:'eventos_inventario',entradas:'eventos_entradas',compras:'eventos_compras'}))items.push({name:'Readiness OAuth2 '+service,request:{method:'GET',url:'http://'+service+':'+({eventos:8081,inventario:8082,entradas:8083,compras:8084}[service])+'/internal/security/readiness',header:[{key:'Authorization',value:'Bearer {{token_'+key+'}}'}]},event:test(200)});
 for(const [service,route,port]of [['eventos','/api-docs',8081],['compras','/v3/api-docs',8084]])items.push({name:'OpenAPI administrativo '+service,request:{method:'GET',url:'http://'+service+':'+port+route,header:[{key:'Authorization',value:'Bearer {{adminToken}}'}]},event:test(200)});
 const collection={info:{name:'TicketYa — cobertura directa de 87 controllers y OAuth2',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',description:'Casos de autorización/validación sin efectos comerciales; complementan los flujos positivos ejecutados con Newman por el runner.'},item:items};
 const before=sql(`SELECT (SELECT COUNT(*) FROM ${meta.schemas.compras}.pagos),(SELECT COUNT(*) FROM ${meta.schemas.compras}.reintegros),(SELECT COUNT(*) FROM ${meta.schemas.entradas}.tickets);`);
 const tools=path.resolve(__dirname,'../target/openfeign/newman/node_modules');
 const script=path.join(__dirname,'newman-network.cjs');
 let output;try{output=docker(['run','--interactive','--name',meta.project+'-newman-probes','--network',meta.network,'--mount',`type=bind,source=${tools},target=/tools/node_modules,readonly`,'--mount',`type=bind,source=${script},target=/network.cjs,readonly`,'node:22.21.1-alpine','node','/network.cjs'],JSON.stringify({collection,environment:{values}}),{},180000);}catch(error){if(error.stdout)fs.writeFileSync(path.join(meta.directory,'newman-controller-probes-results.json'),String(error.stdout));throw error;}
 const report=JSON.parse(output);fs.writeFileSync(path.join(meta.directory,'newman-controller-probes-results.json'),JSON.stringify(report,null,2));assert.deepEqual(report.failures,[]);assert.equal(report.requests,100);
 assert.equal(sql(`SELECT (SELECT COUNT(*) FROM ${meta.schemas.compras}.pagos),(SELECT COUNT(*) FROM ${meta.schemas.compras}.reintegros),(SELECT COUNT(*) FROM ${meta.schemas.entradas}.tickets);`),before,'Coverage probes must not change finance/tickets');
 fs.writeFileSync(path.join(meta.directory,'newman-controller-probes.postman_collection.json'),JSON.stringify(collection,null,2));
 fs.writeFileSync(path.join(meta.directory,'newman-controller-probes-results.json'),JSON.stringify(report,null,2));
 return report;
};
