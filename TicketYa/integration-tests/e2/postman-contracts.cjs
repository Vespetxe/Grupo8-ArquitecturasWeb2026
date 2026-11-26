'use strict';
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../../..');
const projects={eventos:'EventoCatalogo_Service',inventario:'inventarioReserva_service',compras:'comprapago_service',entradas:'entradas_service'};
const verbs=['get','post','put','patch','delete'];
const normalize=p=>p.replace(/\{[^}]+\}/g,'{}');
function inventory(){
 const publicDoc=JSON.parse(fs.readFileSync(path.join(root,'docs/entrega2/openapi-publico.json')));
 const exposed=new Map(Object.entries(publicDoc.paths).flatMap(([p,v])=>verbs.filter(m=>v[m]).map(m=>[m+' '+normalize(p),v[m]])));
 const result=[];
 for(const service of Object.keys(projects)){
  const doc=JSON.parse(fs.readFileSync(path.join(root,'docs/entrega1/openapi',service+'.json')));
  for(const [route,methods] of Object.entries(doc.paths))for(const method of verbs)if(methods[method]){
   const pub=exposed.get(method+' '+normalize(route));
   result.push({service,method:method.toUpperCase(),route,public:Boolean(pub),roles:pub?.['x-roles']||[],operation:pub||methods[method],doc});
  }
  result.push({service,method:'GET',route:'/internal/security/readiness',public:false,roles:[],operation:{responses:{200:{description:'Authenticated technical readiness'}}},doc});
 }
 for(const [route,methods]of Object.entries(publicDoc.paths))if(route.startsWith('/auth/'))for(const method of verbs)if(methods[method])result.push({service:'identidad',method:method.toUpperCase(),route,public:true,roles:methods[method]['x-roles']||[],operation:methods[method],doc:publicDoc});
 if(result.length!==87)throw new Error('Controller inventory changed: review coverage before proceeding');
 return result;
}
function generate(){
 const groups=new Map();
 function variable(row,key){
  const names={eventId:'eventId',idEvento:'eventId',evento:'eventId',event:'eventId',sectorId:'sectorId',idSector:'sectorId',venueId:'venueId',staff:'accessId',owner:'clientId',ticketId:'ticketUUID',clienteId:'clientId',idCliente:'clientId',compraId:'purchaseId',orderId:'purchaseId'};
  if(names[key])return names[key];if(key!=='id')return key;
  return row.route.includes('/participants/')?'participantId':row.route.startsWith('/pagos/')?'paymentId':row.route.startsWith('/compras/')?'purchaseId':row.route.includes('/devoluciones/')?'refundId':row.route.includes('/reintegros/')?'originalRefundId':row.route.includes('/reservas/')?'reservationId':row.route.includes('/eventos/')?'eventId':'id';
 }
 function bodyVariables(value,key){
  const names={organizerId:'organizerId',venueId:'venueId',sectorId:'sectorId',idSector:'sectorId',eventoId:'eventId',idEvento:'eventId',compraId:'purchaseId',reservaId:'reservationId',clienteId:'clientId',idCliente:'clientId',orderId:'purchaseId'};
  if(names[key])return '{{'+names[key]+'}}';
  if(key==='startDateTime')return '{{futureDate}}';
  if(Array.isArray(value))return value.map((v,i)=>key==='entradasIds'?'{{numericTicketId}}':key==='idsAsientos'?'{{seat'+(i+1)+'}}':key==='participantIds'?'{{participantId}}':bodyVariables(v));
  if(value&&typeof value==='object')return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,bodyVariables(v,k)]));return value;
 }
 for(const row of inventory()){
  const folder=groups.get(row.service)||[];groups.set(row.service,folder);
  const role=row.roles[0]?.toLowerCase();
  const request={method:row.method,url:'{{'+(row.public?'gatewayUrl':row.service+'Url')+'}}'+row.route.replace(/\{([^}]+)\}/g,(_,id)=>'{{'+variable(row,id)+'}}'),header:[{key:'Accept',value:'application/json'}]};
  const queries=(row.operation.parameters||[]).filter(p=>p.in==='query'&&(p.required||['clienteId','idCliente','compraId','orderId'].includes(p.name)));
  if(row.route==='/tickets'&&!queries.some(p=>p.name==='orderId'))queries.push({name:'orderId'});
  if(queries.length)request.url+='?'+queries.map(p=>p.name+'={{'+variable(row,p.name)+'}}').join('&');
  const caller=row.service==='eventos'?'compras':row.service==='inventario'?(row.route.includes('/reservas')||row.route==='/ocupaciones/liberar'?'compras':'eventos'):row.service==='entradas'?(row.route.includes('/reports/')||row.route.includes('/internal/events/')?'eventos':'compras'):row.route.includes('/internal/orders/')?'entradas':'eventos';
  if(role||!row.public)request.header.push({key:'Authorization',value:'Bearer {{'+(row.public?'jwt_'+role:'token_'+caller+'_'+row.service)+'}}'});
  const content=row.operation.requestBody?.content?.['application/json'];
  if(content){request.header.push({key:'Content-Type',value:'application/json'});request.body={mode:'raw',raw:JSON.stringify(bodyVariables(content.example||{}),null,2).replace(/"(\{\{(?:clientId|organizerId|venueId|sectorId|eventId|reservationId|purchaseId|numericTicketId|participantId|seat1|seat2)\}\})"/g,'$1'),options:{raw:{language:'json'}}};}
  const expected=Number(Object.keys(row.operation.responses||{}).find(k=>/^2\d\d$/.test(k))||200);
  folder.push({name:row.method+' '+row.route,description:(row.public?'Gateway; roles: '+(row.roles.join(', ')||'anónimo'):'Solo red aislada; OAuth2 técnico, no JWT humano')+'. Parámetros/cuerpo: contrato OpenAPI. Para ejecución secuencial utilizar los flujos preparados por run-security --newman.',request,event:[{listen:'test',script:{exec:[`pm.test('Código esperado para el estado preparado',()=>pm.expect(pm.response.code).to.eql(Number(pm.variables.get('expectedStatus')||${expected})));`,`pm.test('JSON válido',()=>{if(pm.response.text() && !pm.response.headers.get('Content-Type')?.includes('text/event-stream')) JSON.parse(pm.response.text());});`],type:'text/javascript'}}]});
 }
 const technical=Object.entries(require('./security-isolation.cjs').SCOPES).map(([pair,scopes])=>{const [caller,target]=pair.split(':'),key=caller+'_'+target;return {name:'OAuth2 '+pair,request:{method:'POST',url:'{{identidadUrl}}/oauth2/token',auth:{type:'basic',basic:[{key:'username',value:caller+'-to-'+target,type:'string'},{key:'password',value:'{{secret_'+key+'}}',type:'string'}]},body:{mode:'urlencoded',urlencoded:[{key:'grant_type',value:'client_credentials'},{key:'scope',value:scopes}]}},event:[{listen:'test',script:{type:'text/javascript',exec:[`pm.test('OAuth2 autorizado',()=>pm.expect(pm.response.code).to.eql(200));pm.collectionVariables.set('token_${key}',pm.response.json().access_token);`]}}]};});
 return {info:{name:'TicketYa — 87 operaciones de controllers',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',description:'Inventario completo para pruebas individuales con estado preparado. Ejecutar la carpeta Flujo completo reproducible contra un entorno nuevo. Las carpetas por servicio requieren IDs/estado del contrato. Los endpoints técnicos solo se prueban dentro de la red aislada. Nunca repetir mutaciones sobre la demo histórica. SSE requiere prueba de streaming finita complementaria.'},variable:['gatewayUrl','eventosUrl','inventarioUrl','comprasUrl','entradasUrl','identidadUrl','jwt_cliente','jwt_organizador','jwt_administrador','jwt_personal_acceso'].map(key=>({key,value:''})),event:[require('./postman-flows.cjs').setup()],item:[{name:'Flujo completo reproducible',item:require('./postman-flows.cjs').items()},{name:'Complementarios sin nuevos cobros — solo red aislada',item:require('./postman-additional.cjs').items()},...[...groups].map(([name,item])=>({name,item})),{name:'OAuth2 técnico — solo red aislada',item:technical}]};
}
if(require.main===module){const dest=path.join(root,'docs/entrega2/postman-openfeign.json');fs.writeFileSync(dest,JSON.stringify(generate(),null,2)+'\n');const keys=['gatewayUrl','identidadUrl','eventosUrl','inventarioUrl','comprasUrl','entradasUrl','organizadorEmail','organizadorPassword','administradorEmail','administradorPassword','personal_accesoEmail','personal_accesoPassword',...Object.keys(require('./security-isolation.cjs').SCOPES).map(k=>'secret_'+k.replace(':','_'))];fs.writeFileSync(path.join(root,'docs/entrega2/postman-openfeign.environment.json'),JSON.stringify({name:'TicketYa — plantilla sin secretos',values:keys.map(key=>({key,value:'',enabled:true,type:key.includes('Password')||key.startsWith('secret_')?'secret':'default'})),_postman_variable_scope:'environment'},null,2)+'\n');console.log('87 controller operations: '+dest);}
module.exports={inventory,generate,normalize};
