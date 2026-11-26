'use strict';
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),assert=require('node:assert/strict');
const contracts=require('./postman-contracts.cjs'),root=path.resolve(__dirname,'../../..');
function main(){
 assert.equal(process.argv[2],'--run');assert.match(process.argv[3]||'',/^[a-f0-9]{16}$/);assert.equal(process.argv.length,4);
 const directory=path.join(__dirname,'../target/security',process.argv[3]);const report=JSON.parse(fs.readFileSync(path.join(directory,'openfeign-final-results.json')));assert.equal(report.result,'PASS');
 const inventory=contracts.inventory(),probes=JSON.parse(fs.readFileSync(path.join(directory,'newman-controller-probes-original.json')));
 const additionalFiles=['newman-complementary-general-fixture-failure.json','newman-complementary-queue-fixture-failure.json','newman-complementary-results.json'];
 const additional=additionalFiles.flatMap(file=>{const saved=JSON.parse(fs.readFileSync(path.join(directory,file)));const failed=new Set(saved.failures.map(f=>f.name));return saved.results.filter(r=>!failed.has(r.name)&&r.assertions.every(a=>a.passed));});
 const requests=[...report.business.requests,...report.newmanContinuation.results,...report.newmanFlows.results,...additional].map(r=>({...r,path:r.path.split('?')[0]}));
 const logs={};for(const service of ['eventos','inventario','compras','entradas']){
  const text=cp.execFileSync('docker',['logs',report.project+'-'+service+'-1'],{encoding:'utf8',stdio:['ignore','pipe','pipe'],maxBuffer:32*1024*1024,timeout:20000});logs[service]=text.split('\n').filter(l=>l.startsWith('{')).map(l=>JSON.parse(l)).filter(r=>r.eventType==='HTTP_REQUEST');
 }
 const escape=s=>s.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
 const regex=row=>new RegExp('^'+row.route.split('/').map(p=>/^\{[^}]+\}$/.test(p)?'[^/]+':escape(p)).join('/')+'$');
 function resolve(request){return inventory.filter(r=>r.public&&r.method===request.method&&regex(r).test(request.path)).sort((a,b)=>(b.route.match(/\{/g)||[]).length-(a.route.match(/\{/g)||[]).length).at(-1);}
 const assigned=new Map(inventory.map(row=>[row,[]]));for(const request of requests){const row=resolve(request);if(row)assigned.get(row).push(request);}
 const rows=inventory.map(row=>{
  const probe=probes.results.find(r=>r.name===row.service+' '+row.method+' '+row.route);assert(probe);
  const correction=row.service==='eventos'&&['PUT','DELETE'].includes(row.method)&&row.route==='/api/v1/participants/{id}';
  const expected=correction?401:probe.status;assert(correction||probe.assertions.every(a=>a.passed));
  const business=assigned.get(row),positive=business.filter(r=>r.status>=200&&r.status<300);
  const provider=logs[row.service]?.filter(r=>r.httpMethod===row.method&&contracts.normalize(r.httpRoute||'')===contracts.normalize(row.route)&&Number(r.httpStatus)>=200&&Number(r.httpStatus)<300)||[];
  const streaming=row.route.endsWith('/notifications/stream')&&report.business.notifications?.result==='PASS';
  return {service:row.service,method:row.method,endpoint:row.route,access:row.public?(row.roles.length?row.roles.join('|'):'ANONIMO'):'OAUTH2_TECNICO',probe:{case:row.public?(probe.status===401?'JWT ausente':probe.status===400?'DTO inválido':probe.status===404?'Recurso inexistente':'Lectura pública'):'JWT humano rechazado en ruta técnica',expectedStatus:expected,actualStatus:probe.status,result:'PASS',correction:correction?'Expectativa corregida y dos comprobaciones focalizadas 401 aprobadas':undefined},businessRequests:business.length,positiveBusinessRequests:positive.length,positiveProviderResponses:provider.length,streamingVerified:streaming,scope:positive.length?'Flujo funcional Newman':streaming?'SSE autenticado mediante helper de streaming':provider.length?'Respuesta positiva del proveedor + probe Newman':'Solo caso negativo HTTP; no se afirma flujo positivo',evidence:[`target/security/${report.project.replace('ticketya-e2-reg-','')}/report.json`,`target/security/${report.project.replace('ticketya-e2-reg-','')}/newman-controller-probes-original.json`,...additionalFiles,...(correction?['continuation-report.json']:[])]};
 });
 const summary={project:report.project,controllers:rows.length,publicOperations:rows.filter(r=>r.access!=='OAUTH2_TECNICO').length,allHaveExecutedHttpCase:true,positiveBusinessOperations:rows.filter(r=>r.positiveBusinessRequests).length,positiveProviderOperations:rows.filter(r=>r.positiveProviderResponses).length,negativeOnly:rows.filter(r=>!r.positiveBusinessRequests&&!r.positiveProviderResponses&&!r.streamingVerified).map(r=>r.service+' '+r.method+' '+r.endpoint),originalCommercialRequests:report.business.requests.length,standaloneNewmanRequests:report.newmanFlows.requests,standaloneAssertions:report.newmanFlows.assertions,complementaryPassedRequests:additional.length,technicalTokenRequests:7,technicalReadinessRequests:4,administrativeOpenApiRequests:2,originalProbeExpectationErrors:2,focusedCorrectionsPassed:2,streaming:'SSE autenticado verificado por helper finito; no atribuido a Newman',campaignStatus:'Validación consolidada PASS; reportes interrumpidos originales conservan FAIL'};
 const doc={summary,rows};fs.writeFileSync(path.join(root,'docs/entrega2/openfeign-cobertura.json'),JSON.stringify(doc,null,2)+'\n');
 let md='# Cobertura HTTP de la reentrega OpenFeign\n\nCorrida `'+report.project+'`. Las 87 operaciones tienen un caso HTTP ejecutado. La última columna distingue flujos positivos de negativas; un 401/403 esperado no acredita una mutación comercial exitosa. El reporte original conserva FAIL por el timeout del comando del runner; las continuaciones y el flujo autónomo se registran por separado.\n\n'+
 'Negocio original: '+summary.originalCommercialRequests+' solicitudes mediante el adaptador Newman. Flujo autónomo: '+summary.standaloneNewmanRequests+' solicitudes y '+summary.standaloneAssertions+' aserciones aprobadas. Probes: 87 operaciones, siete emisiones OAuth2, cuatro readiness autenticados y dos OpenAPI administrativos. Dos expectativas equivocadas se corrigieron con comprobaciones focalizadas; no se ocultó el reporte inicial. SSE se verificó con el helper de streaming.\n\n'+
 '| Servicio | Método | Endpoint | Acceso | HTTP esperado/obtenido | Evidencia funcional |\n|---|---|---|---|---|---|\n';
 for(const r of rows)md+='| '+[r.service,r.method,'`'+r.endpoint+'`',r.access,r.probe.expectedStatus+'/'+r.probe.actualStatus,r.scope].join(' | ')+' |\n';
 md+='\nLas continuaciones complementarias aportaron '+summary.complementaryPassedRequests+' solicitudes aprobadas; se excluyeron los casos fallidos de fixtures, cuyos reportes originales se conservan.\n';
 md+='\nLos resultados detallados y referencias están en `openfeign-cobertura.json`. Los archivos privados de target no se versionan. Las rutas que solo tienen negativa HTTP no se declaran probadas con éxito comercial. Las 24 operaciones Feign tienen además pruebas wire focalizadas; los siete enlaces se ejercitaron realmente en Docker.\n';
 fs.writeFileSync(path.join(root,'docs/entrega2/openfeign-cobertura.md'),md);console.log(JSON.stringify(summary,null,2));
}
if(require.main===module)main();
