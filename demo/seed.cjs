'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const d=require('./lib.cjs');
async function seed(){
 const c=d.readConfig();d.validateCatalog();assert.ok(fs.existsSync(path.join(d.runtime,'ready.json')),'Start the separate demo first');d.attest(c);
 const j=new d.Journal(path.join(d.runtime,'seed-journal.json'));assert.ok(Object.values(j.data).every(x=>x.status==='DONE'),'Uncertain previous write; inspect demo journal before any more writes');
 const s=d.session(c),call=s.call;
 if(!Object.keys(j.data).length){
  assert.deepEqual(await call(null,'GET','/api/v1/venues'),[],'Demo catalog is not empty');
  assert.deepEqual(await call(null,'GET','/api/v1/participants'),[],'Demo participants are not empty');
  assert.deepEqual(await call(null,'GET','/api/v1/events/search'),[],'Demo events are not empty');
 }
 const write=(key,actor,method,url,body,status=200)=>j.step(key,()=>call(actor,method,url,body,status));
 for(const u of c.users.filter(u=>u.role==='CLIENTE'))await write('register:'+u.key,null,'POST','/auth/register',{email:u.email,password:u.password},201);
 for(const u of c.users)await s.user(u.key);
 const artists={};for(const p of d.catalog.participants){const {key,...body}=p;artists[key]=await write('participant:'+key,'admin','POST','/api/v1/participants',body,201);assert.ok(artists[key].participantId>0);}
 // Preferences exist before publication, so favorite-artist notices use the real outbox.
 for(const [key,types,artist,city]of [['ana',['recital'],'bruma','Tandil'],['bruno',['recital','deporte'],'ensamble','Buenos Aires'],['carla',['festival'],'cocina','Rosario']])await write('preferences:'+key,key,'PUT','/api/v1/me/preferences',{favoriteTypes:types,favoriteParticipantIds:[artists[artist].participantId],preferredCity:city});
 const places={};for(const v of d.catalog.venues){
  places[v.key]=await write('venue:'+v.key,v.organizer,'POST','/api/v1/venues',{name:v.name,address:v.address,city:v.city,latitude:v.latitude,longitude:v.longitude},201);assert.ok(places[v.key].venueId>0);
  for(let i=0;i<v.sectors.length;i++){const sector=await write('sector:'+v.key+':'+i,v.organizer,'POST',`/api/v1/venues/${places[v.key].venueId}/sectors`,v.sectors[i],201);assert.ok(sector.sectorId>0);}
 }
 const events={};for(const e of d.catalog.events){const v=d.catalog.venues.find(v=>v.key===e.venue),actor=await s.user(v.organizer);
  events[e.key]=await write('event:'+e.key,v.organizer,'POST','/api/v1/events',{name:e.name,description:e.description,eventType:e.eventType,organizerId:actor.id,venueId:places[e.venue].venueId,startDateTime:e.startDateTime,participantIds:e.participants.map(k=>artists[k].participantId)},201);assert.ok(events[e.key].eventId>0);
  const id=events[e.key].eventId;
  await write('prices:'+e.key,v.organizer,'POST',`/api/v1/events/${id}/prices`,e.prices.map((price,i)=>({sectorId:j.get('sector:'+v.key+':'+i).sectorId,price})),201);
  const published=await write('publish:'+e.key,v.organizer,'PATCH',`/api/v1/events/${id}/publish`);assert.equal(published.status,'PUBLICADO');
  console.log('Catalog prepared: '+e.name);
 }
 for(const scenario of d.catalog.scenarios){
  const e=d.catalog.events.find(e=>e.key===scenario.event),event=events[e.key].eventId,sector=j.get('sector:'+e.venue+':'+scenario.sector).sectorId,client=await s.user(scenario.client),prefix='scenario:'+scenario.key;
  const available=await call(null,'GET',`/disponibilidad/eventos/${event}/sectores/${sector}`);
  const selectionKey=prefix+':selection';
  const selection=await j.step(selectionKey,async()=>{assert.ok(available.disponibles>=scenario.quantity);const ids=available.esNumerado?available.asientosLibres.slice(0,scenario.quantity).map(a=>a.idAsiento):[];assert.ok(!available.esNumerado||ids.length===scenario.quantity);return {ids};});
  const reservation=await write(prefix+':reservation',scenario.client,'POST','/reservas',{idCliente:client.id,idEvento:event,idSector:sector,cantidad:scenario.quantity,...(selection.ids.length?{idsAsientos:selection.ids}:{})},201);assert.ok(reservation.idReserva>0);
  if(scenario.outcome==='reservation')continue;
  const details=selection.ids.length?selection.ids.map(asientoId=>({sectorId:sector,asientoId,cantidad:1})):[{sectorId:sector,cantidad:scenario.quantity}];
  const purchase=await write(prefix+':purchase',scenario.client,'POST','/compras',{clienteId:client.id,eventoId:event,reservaId:reservation.idReserva,detalles:details},201);assert.ok(purchase.id>0);
  assert.equal(Number(purchase.importeTotal),e.prices[scenario.sector]*scenario.quantity);
  const pay=await write(prefix+':payment',scenario.client,'POST','/pagos',{compraId:purchase.id,claveIdempotencia:'demo-'+c.meta.id+'-'+scenario.key,medioPago:'TARJETA_DEBITO'},201);
  assert.equal(pay.estado,scenario.outcome==='rejected'?'RECHAZADO':'APROBADO','Payment uncertain: recover its original attempt manually; never create another');
  const actual=await call(scenario.client,'GET','/compras/'+purchase.id);assert.equal(actual.estado,scenario.outcome==='rejected'?'FALLIDA':scenario.outcome==='partial-refund'&&j.data[prefix+':refund']?'DEVUELTA_PARCIAL':'COMPLETADA','Purchase not finalized; inspect, no automatic payment replay');
  if(scenario.outcome!=='rejected')await write(prefix+':staff','admin','PUT',`/admin/accesos/eventos/${event}/personal/${(await s.user('acceso')).id}`);
  if(scenario.outcome==='partial-refund'){
   const units=await call(scenario.client,'GET',`/compras/${purchase.id}/entradas`);assert.equal(units.length,scenario.quantity);
   const selected=await j.step(prefix+':refund-selection',async()=>({entryId:units[0].entradaId}));assert.ok(selected.entryId>0);
   const refund=await write(prefix+':refund-request',scenario.client,'POST','/devoluciones',{compraId:purchase.id,clienteId:client.id,entradasIds:[selected.entryId],motivo:'Cambio de planes del cliente ficticio de demostración'},201);
   await write(prefix+':refund-approval','admin','POST',`/admin/devoluciones/${refund.id}/aprobar`,{motivo:'Devolución parcial de la demostración aprobada'});
   const result=await write(prefix+':refund','admin','POST',`/admin/devoluciones/${refund.id}/procesar`,{claveIdempotencia:'demo-refund-'+c.meta.id,importe:refund.importeSolicitado});assert.equal(result.etapa,'FINALIZADA');assert.equal(result.reintegro.estado,'COMPLETADO');
  }
 }
 d.save(path.join(d.runtime,'seed-complete.json'),{project:c.meta.project,completedAt:new Date().toISOString()});
 await require('./verify.cjs').verify(c);console.log('Demo population completed. Accesses are in demo/runtime/ACCESOS-DEMO.md.');
}
if(require.main===module)d.main(()=>d.seedLock(d.runtime,seed));
