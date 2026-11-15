'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{execFileSync}=require('node:child_process');
module.exports=async function({meta,context,load,provisioned}){
 const {api,clients,admin,access,sql}=context;
 const purchase=load.purchases[0],client=clients.find(c=>c.id===purchase.clientId),staff=provisioned.find(p=>p.role==='PERSONAL_ACCESO');
 assert.ok(client&&staff);
 const tickets=await api('GET','/tickets?orderId='+purchase.purchaseId,undefined,200,client);
 const assignment=await api('PUT',`/admin/accesos/eventos/${load.event}/personal/${access.id}`,undefined,200,admin);assert.equal(assignment.assigned,true);
 const before=[sql(`SELECT id,estado,importe,importe_reintegrado FROM ${meta.schemas.compras}.pagos ORDER BY id;`),sql(`SELECT id,estado,importe FROM ${meta.schemas.compras}.reintegros ORDER BY id;`),sql(`SELECT ticket_id,status FROM ${meta.schemas.entradas}.tickets ORDER BY ticket_id;`)];
 const input=path.join(meta.directory,'frontend-qr-private.json');fs.writeFileSync(input,JSON.stringify({project:meta.project,port:meta.port,staff:{email:staff.email,password:staff.password},event:load.event,qr:tickets[0].qr}),{mode:0o600,flag:'wx'});
 const frontend=path.resolve(__dirname,'../../frontend');
 try{execFileSync(process.execPath,[path.join(frontend,'node_modules/@playwright/test/cli.js'),'test','--config','playwright.delivery.config.js'],{cwd:frontend,env:{...process.env,TY_DELIVERY_INPUT:input,TY_DELIVERY_PORT:String(meta.port)},stdio:'pipe',timeout:90000,maxBuffer:4*1024*1024});}catch{throw new Error('Frontend accepted QR check failed; see ignored delivery artifacts; credentials redacted');}
 const after=[sql(`SELECT id,estado,importe,importe_reintegrado FROM ${meta.schemas.compras}.pagos ORDER BY id;`),sql(`SELECT id,estado,importe FROM ${meta.schemas.compras}.reintegros ORDER BY id;`),sql(`SELECT ticket_id,status FROM ${meta.schemas.entradas}.tickets ORDER BY ticket_id;`)];assert.deepEqual(after,before);
 assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.tickets WHERE ticket_id='${tickets[0].ticketId}' AND validated_at IS NOT NULL AND validated_by=${access.id};`),'1');
 return {result:'PASS',realStaffLogin:true,administratorAssigned:true,event:load.event,ticketId:tickets[0].ticketId,accepted:1,repeatRejected:true,invalidRejected:true,financialSnapshotUnchanged:true};
};
