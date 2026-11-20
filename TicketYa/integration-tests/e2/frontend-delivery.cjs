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
 // A separate event and original simulated uncertain attempt exercise the UI repair.
 const e=await context.event(false,15000,'frontend-payment-recovery');const buyer=clients[0];
 const reservation=await api('POST','/reservas',{idCliente:buyer.id,idEvento:e.event,idSector:e.sector,cantidad:1},201,buyer);
 const c=await api('POST','/compras',{clienteId:buyer.id,eventoId:e.event,reservaId:reservation.idReserva,detalles:[{sectorId:e.sector,cantidad:1}]},201,buyer);
 const key='simular-timeout-'+meta.id+'-frontend';
 const original=await api('POST','/pagos',{compraId:c.id,claveIdempotencia:key,medioPago:'TARJETA_DEBITO'},201,buyer);
 assert.equal(original.estado,'RESULTADO_DESCONOCIDO');assert.equal((await api('GET','/compras/'+c.id,undefined,200,buyer)).estado,'PROCESANDO');
 const recoveryInput=path.join(meta.directory,'frontend-recovery-private.json');
 fs.writeFileSync(recoveryInput,JSON.stringify({project:meta.project,port:meta.port,client:{email:buyer.email,password:buyer.password},purchase:c.id}),{mode:0o600,flag:'wx'});
 try{execFileSync(process.execPath,[path.join(frontend,'node_modules/@playwright/test/cli.js'),'test','--config','playwright.delivery.config.js'],{cwd:frontend,env:{...process.env,TY_PAYMENT_RECOVERY_INPUT:recoveryInput,TY_DELIVERY_PORT:String(meta.port)},stdio:'pipe',timeout:90000,maxBuffer:4*1024*1024});}catch{throw new Error('Frontend original payment recovery failed; see ignored delivery artifacts; credentials redacted');}
 const payments=await api('GET','/pagos?compraId='+c.id,undefined,200,buyer);assert.equal(payments.length,1);assert.equal(payments[0].id,original.id);assert.equal(payments[0].claveIdempotencia,key);assert.equal(payments[0].estado,'APROBADO');assert.equal(Number(payments[0].importe),15000);
 assert.equal((await api('GET','/compras/'+c.id,undefined,200,buyer)).estado,'COMPLETADA');
 assert.equal((await api('GET','/tickets?orderId='+c.id,undefined,200,buyer)).length,1);
 assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.pagos WHERE compra_id=${c.id};`),'1');
 assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.compras}.operaciones_simuladas WHERE clave='cobro:${key}';`),'1');
 assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.ticket_batches WHERE order_id='${c.id}';`),'1');
 assert.equal(sql(`SELECT COUNT(*) FROM ${meta.schemas.entradas}.tickets t JOIN ${meta.schemas.entradas}.ticket_batches b ON b.id=t.batch_id WHERE b.order_id='${c.id}';`),'1');
 const previousFinance=[sql(`SELECT id,estado,importe,importe_reintegrado FROM ${meta.schemas.compras}.pagos WHERE id<>${original.id} ORDER BY id;`),sql(`SELECT id,estado,importe FROM ${meta.schemas.compras}.reintegros ORDER BY id;`)];assert.deepEqual(previousFinance,before.slice(0,2));
 return {result:'PASS',realStaffLogin:true,administratorAssigned:true,event:load.event,ticketId:tickets[0].ticketId,accepted:1,repeatRejected:true,invalidRejected:true,financialSnapshotUnchanged:true,paymentRecovery:{purchase:c.id,payment:original.id,sameOriginalAttempt:true,approvedAmount:15000,payments:1,tickets:1,batches:1,previousFinancialRecordsUnchanged:true}};
};
