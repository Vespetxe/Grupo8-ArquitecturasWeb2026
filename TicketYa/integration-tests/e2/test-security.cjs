'use strict';
const {spawnSync}=require('node:child_process');
const path=require('node:path'),fs=require('node:fs'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../..');
const projects=['api-gateway','EventoCatalogo_Service','inventarioReserva_service','entradas_service','comprapago_service','identidad_service'];
assert.ok(process.argv.length===2 || (process.argv.length===3&&projects.includes(process.argv[2])));
for(const project of process.argv[2]?[process.argv[2]]:projects){
 const dir=path.join(root,'TicketYa',project);
 const r=spawnSync('docker',['run','--rm','--mount',`type=bind,source=${dir},target=/workspace`,'--mount','type=volume,source=ticketya-e2-security-maven-cache,target=/root/.m2','-w','/workspace','maven:3.9.11-eclipse-temurin-21','mvn','--batch-mode','--no-transfer-progress','package'],{encoding:'utf8',maxBuffer:32*1024*1024});
 fs.mkdirSync(path.join(dir,'target'),{recursive:true});fs.writeFileSync(path.join(dir,'target','security-tests.log'),(r.stdout||'')+(r.stderr||''));
 console.log(project+': '+(r.status===0?'PASS':'FAIL'));
 console.log((r.stdout||'').split('\n').filter(l=>l.includes('Tests run:')||(l.includes('[ERROR]')&&l.length<450)).join('\n'));
 if(r.status!==0){process.exitCode=1;break;}
}
