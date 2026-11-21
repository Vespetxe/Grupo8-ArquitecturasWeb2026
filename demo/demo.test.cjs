'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const d=require('./lib.cjs');
test('catalog has future fictitious events and consistent references, sectors and prices',()=>{d.validateCatalog(undefined,Date.parse('2026-11-20T12:00:00-03:00'));assert.equal(d.catalog.venues.length,8);assert.equal(d.catalog.venues.reduce((n,v)=>n+v.sectors.length,0),16);assert.equal(d.catalog.users.length,7);assert.equal(d.catalog.participants.length,12);assert.equal(new Set(d.catalog.venues.map(v=>v.city)).size,6);});
test('expired data refuses a later load rather than changing existing dates',()=>assert.throws(()=>d.validateCatalog(undefined,Date.parse('2027-03-01T00:00:00Z'))));
test('numbered sector capacity and scenario purchase limit are checked',()=>{const c=structuredClone(d.catalog);c.venues[1].sectors[0].capacity++;assert.throws(()=>d.validateCatalog(c,Date.parse('2026-11-20')));const c2=structuredClone(d.catalog);c2.scenarios[0].quantity=7;assert.throws(()=>d.validateCatalog(c2,Date.parse('2026-11-20')));});
test('only the rejected scenario exceeds the mock gateway threshold',()=>{for(const x of d.catalog.scenarios){const e=d.catalog.events.find(e=>e.key===x.event);assert.equal(e.prices[x.sector]*x.quantity>100000,x.outcome==='rejected');}});
test('fresh namespace differs from E2 projects, volumes and business databases',()=>{const m=d.meta('abcdef0123456789');assert.equal(m.project,'ticketya-demo-abcdef0123456789');assert.equal(m.port,49080);assert.ok(Object.values(m.schemas).every(s=>s.startsWith('ty_demo_')));assert.match(m.identitySchema,/^ty_e2_identity_[a-f0-9]{16}$/);assert.ok(!m.mysqlVolume.startsWith('ticketya-e2-'));assert.throws(()=>d.meta('../../persistent'));});
test('Compose exposes only Gateway on loopback, never reuses a host data directory',()=>{const s=d.spec(d.meta('abcdef0123456789'));assert.equal(Object.keys(s.services).length,8);const ports=Object.entries(s.services).filter(([,v])=>v.ports);assert.equal(ports.length,1);assert.equal(ports[0][0],'gateway');assert.equal(ports[0][1].ports[0].host_ip,'127.0.0.1');for(const v of Object.values(s.volumes))assert.ok(!v.external);for(const v of Object.values(s.networks))assert.ok(!v.external);for(const a of Object.values(s.services)){assert.ok(!a.container_name);assert.equal(a.pull_policy,'never');assert.ok(a.mem_limit);for(const m of a.volumes||[])if(m.type==='bind')assert.ok(!m.target.startsWith('/var/lib/mysql')&&!m.target.startsWith('/data/db'));}});
test('business DB owner and internal URLs match existing contracts',()=>{const m=d.meta('abcdef0123456789'),s=d.spec(m);for(const n of ['eventos','inventario','entradas','compras']){assert.match(s.services[n].environment.DB_URL,new RegExp('/'+m.schemas[n]+'\\?'));assert.equal(s.services[n].environment.DB_USERNAME,'demo_'+n);assert.equal(s.services[n].environment.SPRING_JPA_HIBERNATE_DDL_AUTO,'update');}assert.equal(s.services.identidad.environment.DB_USERNAME,'ty_identidad');assert.equal(s.services.gateway.environment.IDENTIDAD_BASE_URL,'http://identidad:8085');assert.equal(s.services.eventos.environment.COMPRAS_BASE_URL,'http://compras:8084');assert.ok(s.services.eventos.environment.MONGODB_URI.includes(m.mongoDatabase));});
test('only Identity mounts a private key directory; other apps mount public key read-only',()=>{const s=d.spec(d.meta('abcdef0123456789'));for(const n of ['gateway','eventos','inventario','entradas','compras']){assert.equal(s.services[n].volumes.length,1);assert.ok(s.services[n].volumes[0].source.endsWith('public.pem'));assert.equal(s.services[n].volumes[0].read_only,true);}assert.equal(s.services.identidad.volumes[0].target,'/keys');assert.equal(s.services.mongo.environment.TY_E2_EVENTOS_MONGO_PASSWORD,'${TY_DEMO_MONGO_EVENTOS_PASSWORD:?required}');});
test('both Mongo data directories use new named demo volumes, never implicit anonymous storage',()=>{const m=d.meta('abcdef0123456789'),s=d.spec(m);assert.ok(m.mongoConfigVolume.startsWith(m.project));assert.ok(s.volumes.mongo_config);assert.deepEqual(s.services.mongo.volumes.filter(v=>v.type==='volume').map(v=>[v.source,v.target]),[['mongo_data','/data/db'],['mongo_config','/data/configdb']]);});
test('seven copied technical scopes still match the current service policies',()=>{const source=fs.readFileSync(path.join(d.root,'TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/RoutePolicy.java'),'utf8');for(const [pair,scopes]of Object.entries(d.calls)){assert.ok(source.includes('"'+pair+'",java.util.List.of('+scopes.split(' ').map(s=>'"'+s+'"').join(',')+')'));}assert.equal(Object.keys(d.calls).length,7);});
test('subnet overlap recognizes Docker ranges without executing Docker',()=>{assert.ok(d.overlap('10.205.171.0/24','10.205.0.0/16'));assert.ok(d.overlap('10.205.171.0/24','10.205.171.128/25'));assert.ok(!d.overlap('10.205.171.0/24','10.203.171.0/24'));});
test('memory preflight parses Docker units and rejects unrecognized values',()=>{assert.equal(d.memoryBytes('768MiB '),768*1024**2);assert.equal(d.memoryBytes('1.5GiB'),1.5*1024**3);assert.equal(d.memoryBytes('100MB'),1e8);assert.throws(()=>d.memoryBytes('unknown'));});
test('completed operation persists its returned real ID and is not executed twice',async()=>{const directory=fs.mkdtempSync(path.join(os.tmpdir(),'ticketya-demo-journal-')),file=path.join(directory,'journal.json');const j=new d.Journal(file);let writes=0;const first=await j.step('venue',async()=>{writes++;return {venueId:821};});const second=await new d.Journal(file).step('venue',async()=>{writes++;return {venueId:999};});assert.deepEqual(first,second);assert.equal(writes,1);});
test('lost response leaves durable PENDING and blocks a second write after restart',async()=>{const directory=fs.mkdtempSync(path.join(os.tmpdir(),'ticketya-demo-uncertain-')),file=path.join(directory,'journal.json');const j=new d.Journal(file);let writes=0;await assert.rejects(j.step('payment',async()=>{writes++;throw Error('response lost');}));await assert.rejects(new d.Journal(file).step('payment',async()=>{writes++;return {};}));assert.equal(writes,1);assert.equal(new d.Journal(file).data.payment.status,'PENDING');});
test('exclusive seed lock rejects concurrent loaders and preserves failed-run evidence',async()=>{const directory=fs.mkdtempSync(path.join(os.tmpdir(),'ticketya-demo-lock-'));let release;const first=d.seedLock(directory,()=>new Promise(r=>{release=r;}));let writes=0;await assert.rejects(d.seedLock(directory,async()=>{writes++;}));assert.equal(writes,0);release();await first;await assert.rejects(d.seedLock(directory,async()=>{throw Error('uncertain');}));assert.ok(fs.existsSync(path.join(directory,'seed.lock')));await assert.rejects(d.seedLock(directory,async()=>{writes++;}));assert.equal(writes,0);});
test('new scripts do not call E2 runners, deletion commands or automatic financial recovery',()=>{for(const n of ['prepare.cjs','start.cjs','seed.cjs','verify.cjs','lib.cjs']){const source=fs.readFileSync(path.join(__dirname,n),'utf8');assert.ok(!source.includes('run-security.cjs'));assert.ok(!source.includes('--force-recreate'));assert.ok(!source.includes('--renew-anon-volumes'));assert.ok(!source.includes("'down'"));assert.ok(!source.includes("'rm'"));assert.ok(!source.includes("'prune'"));}const seed=fs.readFileSync(path.join(__dirname,'seed.cjs'),'utf8');assert.ok(!seed.includes('INSERT INTO'));assert.ok(!seed.includes('DELETE FROM'));});

// Offline contract fixtures for the final verifier. No Docker, HTTP, Mongo or SQL.
function coherentVerificationFixture(){
 const responses=new Map(),journal={},owners={admin:11,sierras:12,urbanos:13,acceso:14,ana:15,bruno:16,carla:17};
 const c={meta:d.meta('abcdef0123456789'),users:structuredClone(d.catalog.users)},now=Date.now(),timestamp=new Date(now).toISOString();
 const put=(url,value,actor='*')=>responses.set(actor+' '+url,structuredClone(value));
 const eventIds={},sectorIds={},metricsBySector={},ticketByScenario={};
 const metric=(capacity,sold=0,returned=0,gross=0,refund=0)=>({soldTickets:sold,returnedTickets:returned,netSoldTickets:sold-returned,grossCollected:gross,refundedAmount:refund,netCollected:gross-refund,capacity,available:capacity-sold+returned,occupancyPercent:Math.round((sold-returned)*10000/capacity)/100,issuedTickets:sold,activeTickets:sold-returned,annulledTickets:returned,admissions:0});
 d.catalog.participants.forEach((p,i)=>{journal['participant:'+p.key]={participantId:201+i};});
 d.catalog.venues.forEach((v,i)=>{journal['venue:'+v.key]={venueId:301+i};v.sectors.forEach((s,k)=>{const sid=401+i*2+k;sectorIds[v.key+':'+k]=sid;journal['sector:'+v.key+':'+k]={sectorId:sid};});});
 d.catalog.events.forEach((e,i)=>{
  const event=501+i,v=d.catalog.venues.find(v=>v.key===e.venue);eventIds[e.key]=event;journal['event:'+e.key]={eventId:event};
  put('/api/v1/events/'+event,{eventId:event,name:e.name,status:'PUBLICADO',venueId:journal['venue:'+v.key].venueId,sectorPrices:e.prices.map((price,k)=>({sectorId:sectorIds[v.key+':'+k],price}))});
  v.sectors.forEach((sector,k)=>{const sid=sectorIds[v.key+':'+k];metricsBySector[event+':'+sid]=metric(sector.capacity);put(`/disponibilidad/eventos/${event}/sectores/${sid}`,{idEvento:event,idSector:sid,esNumerado:sector.type==='NUMERADO',capacidadTotal:sector.capacity,disponibles:sector.capacity,asientosLibres:sector.type==='NUMERADO'?Array.from({length:sector.capacity},(_,n)=>({idAsiento:sid*1000+n+1,fila:'1',numero:n+1})):[]});});
 });
 d.catalog.scenarios.forEach((x,i)=>{
  const prefix='scenario:'+x.key,e=d.catalog.events.find(e=>e.key===x.event),event=eventIds[x.event],sid=sectorIds[e.venue+':'+x.sector],stock=responses.get('* '+`/disponibilidad/eventos/${event}/sectores/${sid}`),rid=801+i,pid=601+i,payid=701+i,unit=e.prices[x.sector];
  const selection=stock.esNumerado?stock.asientosLibres.slice(0,x.quantity).map(a=>a.idAsiento):[];
  journal[prefix+':reservation']={idReserva:rid};journal[prefix+':selection']={ids:selection};
  put('/reservas/'+rid,{idReserva:rid,idCliente:owners[x.client],idEvento:event,idSector:sid,cantidad:x.quantity,estado:x.outcome==='reservation'?'PENDIENTE':x.outcome==='rejected'?'LIBERADA':'CONFIRMADA',fechaExpiracion:new Date(now+600000).toISOString().replace('Z',''),asientosReservados:selection.map(idAsiento=>({idAsiento,fila:'1',numero:1}))});
  if(x.outcome==='reservation'){stock.disponibles-=x.quantity;return;}
  journal[prefix+':purchase']={id:pid};journal[prefix+':payment']={id:payid};
  put('/compras/'+pid,{id:pid,clienteId:owners[x.client],eventoId:event,reservaId:rid,estado:x.outcome==='rejected'?'FALLIDA':x.outcome==='partial-refund'?'DEVUELTA_PARCIAL':'COMPLETADA',etapa:'FINALIZADA',importeTotal:unit*x.quantity,detalles:selection.length?selection.map(asientoId=>({sectorId:sid,asientoId,cantidad:1,precioUnitario:unit,subtotal:unit})):[{sectorId:sid,asientoId:null,cantidad:x.quantity,precioUnitario:unit,subtotal:unit*x.quantity}]});
  put('/pagos?compraId='+pid,[{id:payid,compraId:pid,claveIdempotencia:'demo-'+c.meta.id+'-'+x.key,medioPago:'TARJETA_DEBITO',estado:x.outcome==='rejected'?'RECHAZADO':x.outcome==='partial-refund'?'REINTEGRADO_PARCIAL':'APROBADO',importe:unit*x.quantity,importeReintegrado:x.outcome==='partial-refund'?unit:0}]);
  const tickets=x.outcome==='rejected'?[]:Array.from({length:x.quantity},(_,n)=>({ticketId:`00000000-0000-4000-8000-${String(pid*10+n).padStart(12,'0')}`,qr:`10000000-0000-4000-8000-${String(pid*10+n).padStart(12,'0')}`,orderId:String(pid),eventId:String(event),clientId:String(owners[x.client]),sectorId:String(sid),seatId:selection.length?String(selection[n]):null,status:x.outcome==='partial-refund'&&n===0?'ANULADA':'EMITIDA'}));
  ticketByScenario[x.key]=tickets;put('/tickets?orderId='+pid,tickets);
  if(x.outcome!=='rejected'){const returned=x.outcome==='partial-refund'?1:0;metricsBySector[event+':'+sid]=metric(stock.capacidadTotal,x.quantity,returned,unit*x.quantity,returned*unit);stock.disponibles-=x.quantity-returned;if(selection.length)stock.asientosLibres=stock.asientosLibres.filter(a=>!selection.includes(a.idAsiento));}
  if(x.outcome==='partial-refund'){
   const request={id:1001,compraId:pid,clienteId:owners[x.client],eventoId:event,estado:'COMPLETADA',importeSolicitado:unit,importeReintegrado:unit,detalles:[{entradaId:2001,importe:unit}]};
   const refund={id:1002,pagoId:payid,devolucionId:1001,claveIdempotencia:'demo-refund-'+c.meta.id,importe:unit,estado:'COMPLETADO'};
   journal[prefix+':refund-request']=structuredClone(request);journal[prefix+':refund-selection']={entryId:2001};journal[prefix+':refund']={reintegro:structuredClone(refund)};
   put('/admin/devoluciones/1001/procesamiento',{etapa:'FINALIZADA',cancelacionEvento:false,devolucion:request,reintegro:refund});put('/compras/'+pid+'/entradas',tickets.map((t,n)=>({entradaId:2001+n,ticketId:t.ticketId})));
  }
 });
 for(const [u,types,artist,city]of [['ana',['recital'],'bruma','tandil'],['bruno',['recital','deporte'],'ensamble','buenos aires'],['carla',['festival'],'cocina','rosario']]){
  put('/api/v1/me/preferences',{favoriteTypes:types,favoriteParticipantIds:[journal['participant:'+artist].participantId],preferredCity:city,updatedAt:timestamp},u);
  const bought=new Set(d.catalog.scenarios.filter(x=>x.client===u&&['approved','partial-refund'].includes(x.outcome)).map(x=>x.event));
  put('/api/v1/me/recommendations',{historyAvailable:true,events:d.catalog.events.filter(e=>!bought.has(e.key)).slice(0,10).map(e=>({id:eventIds[e.key],date:e.startDateTime}))},u);
  put('/api/v1/me/notifications',[],u);
 }
 for(const key of ['risas-plata','cuerdas-camara','sabores-ribera']){
  const e=d.catalog.events.find(e=>e.key===key),v=d.catalog.venues.find(v=>v.key===e.venue),event=eventIds[key];
  const sectors=v.sectors.map((s,k)=>{const sid=sectorIds[v.key+':'+k];return {sectorId:sid,inventoryState:'HABILITADO',ticketsMatchSales:true,metrics:metricsBySector[event+':'+sid]};});
  const totals={};for(const field of Object.keys(sectors[0].metrics))if(field!=='occupancyPercent')totals[field]=sectors.reduce((n,s)=>n+s.metrics[field],0);totals.occupancyPercent=Math.round(totals.netSoldTickets*10000/totals.capacity)/100;
  put(`/api/v1/events/${event}/report`,{eventId:event,eventName:e.name,eventStatus:'PUBLICADO',financialComplete:true,sourcesAligned:true,amountUnit:'UNIDAD_MONETARIA_DEL_SISTEMA',period:'ACUMULADO_DEL_EVENTO',generatedAt:timestamp,sourceGeneratedAt:{compras:timestamp,entradas:timestamp,inventario:timestamp},sectors,totals});
 }
 return {c,journal,responses,eventIds,sectorIds,owners,put,get:(url,actor='*')=>responses.get(actor+' '+url)};
}
async function runMockVerifier(t,fixture){
 const saved=[],errors=[],calls=[];
 t.mock.method(fs,'existsSync',()=>true);t.mock.method(d,'attest',()=>{});
 t.mock.method(d,'Journal',function(){return {get:key=>{assert.ok(key in fixture.journal,'Fixture journal key '+key);return structuredClone(fixture.journal[key]);}};});
 t.mock.method(d,'session',()=>({user:async key=>({id:fixture.owners[key]}),call:async(actor,method,url)=>{assert.equal(method,'GET','Verifier must never send business writes');calls.push([actor,method,url]);const value=fixture.responses.get(actor+' '+url)??fixture.responses.get('* '+url);assert.notEqual(value,undefined,'Missing fixture response '+url);return structuredClone(value);}}));
 t.mock.method(d,'save',(file,value)=>{assert.equal(file,path.join(d.runtime,'verification.json'));saved.push(structuredClone(value));});t.mock.method(console,'log',()=>{});t.mock.method(console,'error',message=>errors.push(message));
 return {saved,errors,calls,run:()=>require('./verify.cjs').verify(fixture.c)};
}

test('final verifier PASS: coherent payments, final stages, reservations, stock 899, Mongo preferences and sector reports',async t=>{const f=coherentVerificationFixture(),v=await runMockVerifier(t,f);const result=await v.run();assert.equal(result.result,'PASS');assert.equal(v.saved.at(-1).result,'PASS');assert.equal(result.scenarios.length,5);assert.equal(v.errors.length,0);assert.ok(v.calls.every(c=>c[1]==='GET'));assert.ok(result.limitations.length>0);});
test('final verifier PASS after natural expiration: expired reservation restores its two places',async t=>{const f=coherentVerificationFixture();f.get('/reservas/805').estado='EXPIRADA';f.get('/reservas/805').fechaExpiracion=new Date(Date.now()-60000).toISOString();const event=f.eventIds['bruma-tandil'],sid=f.sectorIds['parque:0'];f.get(`/disponibilidad/eventos/${event}/sectores/${sid}`).disponibles=1200;const v=await runMockVerifier(t,f);assert.equal((await v.run()).result,'PASS');});
const badVerificationCases=[
 ['Astra: pagos PENDIENTE con importe 1',f=>{for(const x of d.catalog.scenarios.filter(x=>x.outcome!=='reservation')){const p=f.journal['scenario:'+x.key+':purchase'].id;Object.assign(f.get('/pagos?compraId='+p)[0],{estado:'PENDIENTE',importe:1});}},/Estado del pago/],
 ['Astra: importe 1 aun con estado APROBADO',f=>{f.get('/pagos?compraId=601')[0].importe=1;},/Importe del pago/],
 ['Astra: compra permanece en etapa PAGO',f=>{f.get('/compras/601').etapa='PAGO';},/Etapa final de compra/],
 ['Astra: reserva LIBERADA con entradas vigentes',f=>{f.get('/reservas/801').estado='LIBERADA';},/Estado de reserva/],
 ['Astra: stock 900 en vez de 899 tras devolucion parcial',f=>{f.get(`/disponibilidad/eventos/${f.eventIds['sabores-ribera']}/sectores/${f.sectorIds['ribera:0']}`).disponibles=900;},/Stock disponible/],
 ['Astra: preferencias Mongo vacias',f=>{f.put('/api/v1/me/preferences',{favoriteTypes:[],favoriteParticipantIds:[],preferredCity:null,updatedAt:null},'ana');},/Tipos favoritos/],
 ['Astra: reporte vacio',f=>{f.put(`/api/v1/events/${f.eventIds['risas-plata']}/report`,{});},/Evento del reporte/],
 ['reintegro acumulado incorrecto',f=>{f.get('/pagos?compraId=603')[0].importeReintegrado=0;},/Reintegro acumulado/],
 ['pago parcial sigue APROBADO',f=>{f.get('/pagos?compraId=603')[0].estado='APROBADO';},/Estado del pago/],
 ['reintegro aun PENDIENTE',f=>{f.get('/admin/devoluciones/1001/procesamiento').reintegro.estado='PENDIENTE';},/Estado del reintegro/],
 ['reintegro de importe equivocado',f=>{f.get('/admin/devoluciones/1001/procesamiento').reintegro.importe=1;},/Importe del reintegro/],
 ['devolucion no completada',f=>{f.get('/admin/devoluciones/1001/procesamiento').devolucion.estado='APROBADA';},/Estado de devolución/],
 ['entrada devuelta distinta de la anulada',f=>{f.get('/compras/603/entradas')[0].ticketId=f.get('/tickets?orderId=603')[1].ticketId;},/entrada anulada/],
 ['reserva rechazada no fue liberada',f=>{f.get('/reservas/804').estado='CONFIRMADA';},/Estado de reserva/],
 ['reserva temporal vencida todavia PENDIENTE',f=>{f.get('/reservas/805').fechaExpiracion=new Date(Date.now()-60000).toISOString();},/vencida aún PENDIENTE/],
 ['reserva EXPIRADA antes del vencimiento',f=>{f.get('/reservas/805').estado='EXPIRADA';},/antes de su vencimiento/],
 ['reporte omite una devolucion en los totales',f=>{f.get(`/api/v1/events/${f.eventIds['sabores-ribera']}/report`).totals.returnedTickets=0;},/Totales.*returnedTickets/],
 ['reporte entrega recaudacion neta incorrecta',f=>{f.get(`/api/v1/events/${f.eventIds['sabores-ribera']}/report`).totals.netCollected=28000;},/Totales.*netCollected/],
 ['reporte financiero incompleto con neto null',f=>{const r=f.get(`/api/v1/events/${f.eventIds['sabores-ribera']}/report`);r.financialComplete=false;r.totals.netCollected=null;},/financiero incompleto/],
 ['reporte usa ocupacion equivocada',f=>{f.get(`/api/v1/events/${f.eventIds['risas-plata']}/report`).totals.occupancyPercent=100;},/ocupación/],
 ['preferencias de otro cliente',f=>{f.put('/api/v1/me/preferences',f.get('/api/v1/me/preferences','bruno'),'ana');},/Tipos favoritos|Participantes favoritos|Ciudad preferida/],
 ['preferencias sin fecha persistida',f=>{f.get('/api/v1/me/preferences','ana').updatedAt=null;},/Actualización de preferencias/],
 ['ticket ajeno a la compra',f=>{f.get('/tickets?orderId=601')[0].orderId='999';},/Orden de la entrada/],
 ['pago duplicado',f=>{f.get('/pagos?compraId=601').push(structuredClone(f.get('/pagos?compraId=601')[0]));},/exactamente un pago/],
 ['importe null no se interpreta como cero',f=>{f.get('/pagos?compraId=601')[0].importe=null;},/importe inválido/]
];
for(const [name,mutate,message]of badVerificationCases)test('final verifier FAIL: '+name,async t=>{const f=coherentVerificationFixture();mutate(f);const v=await runMockVerifier(t,f);await assert.rejects(v.run(),e=>message.test(e.message));assert.equal(v.saved.at(-1).result,'FAIL');assert.ok(v.errors.some(e=>e.startsWith('FAIL:')));assert.ok(v.calls.every(c=>c[1]==='GET'));assert.ok(!v.saved.some(r=>r.result==='PASS'));});
