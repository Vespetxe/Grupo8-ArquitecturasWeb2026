'use strict';
const assert=require('node:assert/strict'),crypto=require('node:crypto');
module.exports=async function({meta,sql,attest,api,clients,organizers,admin,access,event,active,annulled}){
 const route='/accesos/eventos/'+event+'/validaciones',assignment='/admin/accesos/eventos/'+event+'/personal/'+access.id;
 const scan=(token,expected=200,actor=access,check=true)=>api('POST',route,{qr:token},expected,actor,{},check);
 await scan(active.qr,401,null);
 for(const actor of [clients[0],organizers[0],admin])await scan(active.qr,403,actor);
 await scan(active.qr,403); // correct role, no event assignment
 await api('PUT',assignment,undefined,403,access);await api('PUT',assignment,undefined,403,clients[0]);
 const permit=await api('PUT',assignment,undefined,200,admin);assert.equal(permit.assigned,true);assert.deepEqual(await api('PUT',assignment,undefined,200,admin),permit);
 assert.equal((await scan('invalid',400)).code,'INVALID_QR');assert.equal((await scan(crypto.randomUUID(),404)).code,'TICKET_NOT_FOUND');
 const foreign='qr-other-'+meta.id;await api('PUT','/admin/accesos/eventos/'+foreign+'/personal/'+access.id,undefined,200,admin);
 const mismatch=await api('POST','/accesos/eventos/'+foreign+'/validaciones',{qr:active.qr},409,access);assert.equal(mismatch.code,'EVENT_MISMATCH');
 await api('DELETE',assignment,undefined,200,admin);await scan(active.qr,403);
 assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.tickets WHERE ticket_id='${active.ticketId}' AND validated_at IS NOT NULL;`),'0');
 await api('PUT',assignment,undefined,200,admin);
 assert.equal((await scan(annulled.qr,409)).code,'TICKET_NOT_ENABLED');
 attest();const results=await Promise.all([scan(active.qr,[200,409],access,false),scan(active.qr,[200,409],access,false)]);attest();
 const successes=results.filter(r=>r.status===200),rejected=results.filter(r=>r.status===409);
 assert.equal(successes.length,1);assert.equal(rejected.length,1);assert.equal(successes[0].body.accepted,true);assert.equal(successes[0].body.ticketId,active.ticketId);assert.equal(rejected[0].body.code,'ALREADY_USED');
 assert.equal((await scan(active.qr,409)).code,'ALREADY_USED');
 assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.tickets WHERE validated_at IS NOT NULL;`),'1');
 const persisted=sql(`SELECT CONCAT(validated_at,':',validated_by) FROM ${meta.schemas.entradas}.tickets WHERE ticket_id='${active.ticketId}';`);assert.ok(persisted.endsWith(':'+access.id));
 assert.equal(sql(`SELECT status FROM ${meta.schemas.entradas}.tickets WHERE ticket_id='${active.ticketId}';`),'EMITIDA');
 return {result:'PASS',event,concurrentScans:2,accepted:1,rejected:1,usedTicketId:active.ticketId,staffId:access.id,persistedAdmission:persisted,assignmentRequired:true,revocationVerified:true,annulledRejected:true,wrongEventRejected:true};
};
