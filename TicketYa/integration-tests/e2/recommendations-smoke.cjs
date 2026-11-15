'use strict';
const assert=require('node:assert/strict');
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
module.exports=async function({meta,sql,attest,api,clients,organizers,admin,access,event,scenarios,controls}){
 const prefs='/api/v1/me/preferences',recs='/api/v1/me/recommendations';
 const get=(path,actor=clients[0],expected=200)=>api('GET',path,undefined,expected,actor);
 const put=(body,actor=clients[0],expected=200)=>api('PUT',prefs,body,expected,actor);
 const snapshot=()=>[sql(`SELECT id,estado,importe,importe_reintegrado FROM ${meta.schemas.compras}.pagos ORDER BY id;`),sql(`SELECT clave,datos FROM ${meta.schemas.compras}.operaciones_simuladas ORDER BY clave;`),sql(`SELECT ticket_id,status FROM ${meta.schemas.entradas}.tickets ORDER BY ticket_id;`)];
 const before=snapshot();
 await get(prefs,null,401);for(const actor of [admin,organizers[0],access])await get(recs,actor,403);
 const general=await get(recs,clients[1]);assert.equal(general.historyAvailable,true);assert.equal(general.mode,'GENERAL');
 const bought=scenarios.find(s=>s.name==='pago_incierto_recuperado').event;
 const personalized=await get(recs,clients[2]);assert.equal(personalized.historyAvailable,true);assert.ok(!personalized.events.some(e=>e.id===bought));
 assert.ok(general.events.some(e=>e.id===bought));assert.ok(general.events.every(e=>e.id!==scenarios[0].event));
 const artist=await api('POST','/api/v1/participants',{name:'Favorite '+meta.id,role:'Banda'},201,admin);
 const favorite=await event(false,15000,'recommend-artist',{type:'Conferencia',city:'Azul',participantIds:[artist.participantId]});
 const typed=await event(false,15000,'recommend-type',{type:'Teatro',city:'Azul'});
 await put({favoriteTypes:['recital'],favoriteParticipantIds:[],preferredCity:'Azul'});
 const desired={favoriteTypes:[' TEATRO ','teatro'],favoriteParticipantIds:[artist.participantId,artist.participantId],preferredCity:' Tandil '};
 const updated=await put(desired);assert.deepEqual(updated.favoriteTypes,['teatro']);assert.deepEqual(updated.favoriteParticipantIds,[artist.participantId]);assert.equal(updated.preferredCity,'tandil');
 assert.deepEqual(await get(prefs),updated);assert.deepEqual((await get(prefs+'?userId='+clients[0].id,clients[1])).favoriteTypes,[]);
 await put({favoriteTypes:[],favoriteParticipantIds:[-1],preferredCity:null},clients[0],400);
 const second={favoriteTypes:['recital'],favoriteParticipantIds:[],preferredCity:null};attest();await Promise.all([api('PUT',prefs,second,200,clients[1],{},false),api('PUT',prefs,second,200,clients[1],{},false)]);attest();
 const documents=controls.mongoDocuments();assert.equal(documents.length,2);assert.equal(new Set(documents.map(d=>d._id)).size,2);for(const d of documents)assert.deepEqual(Object.keys(d).sort(),['_id','favoriteParticipantIds','favoriteTypes','preferredCity','updatedAt'].sort());
 const ranked=await get(recs);assert.equal(ranked.events[0].id,favorite.event);assert.equal(ranked.events[1].id,typed.event);assert.ok(ranked.events.length<=10);
 let stopped;
 try{
  controls.stop('compras');stopped='compras';const degraded=await get(recs);assert.equal(degraded.historyAvailable,false);assert.equal(degraded.warning,'HISTORY_UNAVAILABLE');assert.equal(degraded.mode,'PREFERENCES_ONLY');assert.equal(degraded.events[0].id,favorite.event);
  await controls.start(stopped);stopped=undefined;assert.equal((await get(recs)).historyAvailable,true);
  controls.stop('mongo');stopped='mongo';await get(prefs,clients[0],503);await get(recs,clients[0],503);await get('/api/v1/events/search');
  await controls.start(stopped);stopped=undefined;
  let persisted;for(let i=0;i<10;i++){const result=await api('GET',prefs,undefined,[200,503],clients[0]);if(result.status===200){persisted=result.body;break;}await sleep(500);}assert.deepEqual(persisted,updated);assert.equal(controls.mongoDocuments().length,2);
 }finally{if(stopped&&controls.available())await controls.start(stopped);}
 assert.deepEqual(snapshot(),before);
 return {result:'PASS',documents:2,onlyApprovedFields:true,jwtIsolation:true,concurrentPuts:2,ranking:[favorite.event,typed.event],purchasedEventExcluded:bought,historyOutageExplicit:true,mongoOutage503:true,mongoPersistenceAfterRestart:true,catalogueDuringMongoOutage:true,financialSnapshotUnchanged:true};
};
