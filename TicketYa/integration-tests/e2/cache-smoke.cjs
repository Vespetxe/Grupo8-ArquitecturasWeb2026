'use strict';
const assert=require('node:assert/strict');
// Only the canonical isolated commercial runner supplies this authenticated API.
module.exports=async function({api,clients,admin,meta}){
 const base='/api/v1/participants';
 const before=await api('GET',base,undefined,200,null);
 assert.deepEqual(await api('GET',base,undefined,200,clients[0]),before);
 const name='Cache '+meta.id;
 const created=await api('POST',base,{name,role:'Banda'},201,admin);
 const route=base+'/'+created.participantId;
 assert.ok((await api('GET',base,undefined,200,null)).some(p=>p.participantId===created.participantId));
 const first=await api('GET',route,undefined,200,null);
 assert.deepEqual(await api('GET',route,undefined,200,clients[1]),first);
 await api('PUT',route,{name:'Forbidden',role:'Banda'},403,clients[0]);
 assert.deepEqual(await api('GET',route,undefined,200,null),first);
 await api('PUT',route,{name:name+' updated',role:'Solista'},200,admin);
 assert.equal((await api('GET',route,undefined,200,null)).name,name+' updated');
 assert.equal((await api('GET',base,undefined,200,clients[2])).find(p=>p.participantId===created.participantId).role,'Solista');
 await api('GET',base+'/999999999',undefined,404,null);
 return {result:'PASS',participantId:created.participantId,publicReads:true,committedInvalidation:true,clientWriteRejected:true};
};
