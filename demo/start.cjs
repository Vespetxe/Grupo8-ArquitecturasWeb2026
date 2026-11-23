'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),net=require('node:net');
const d=require('./lib.cjs');const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function wait(c,name){const deadline=Date.now()+180000;while(Date.now()<deadline){const container=d.inspect(c,name);if(['exited','dead'].includes(container.State.Status))throw require('../TicketYa/integration-tests/e2/environment.cjs').startupFailure(name,container);if(['mysql','mongo'].includes(name)?container.State.Health?.Status==='healthy':d.docker(['logs','--since',container.State.StartedAt,container.Id],c).includes('Started '))return;await delay(2000);}throw require('../TicketYa/integration-tests/e2/environment.cjs').startupDeadline(name);}
function snapshot(c,ids){if(!ids.length)return [];return JSON.parse(d.docker(['inspect',...ids],c)).map(v=>({id:v.Id,state:v.State.Status,started:v.State.StartedAt,finished:v.State.FinishedAt,restarts:v.RestartCount}));}
async function start(){
 const environment=require('../TicketYa/integration-tests/e2/environment.cjs');environment.nodeVersion();environment.engine(args=>d.docker(args));environment.images(args=>d.docker(args),environment.plan(false,true).map(v=>v.image));
 const c=d.readConfig();d.validateCatalog();const completed=path.join(d.runtime,'ready.json');
 const current=d.docker(['ps','-aq','--filter','label=com.docker.compose.project='+c.meta.project],c).trim();
 if(fs.existsSync(completed)){d.attest(c);console.log('Demo already running; no containers restarted, no provisioning repeated.');return;}
 assert.equal(current,'','Existing or partial demo resources; inspect manually, no automatic restart');
 assert.ok(!fs.existsSync(path.join(d.runtime,'start-intent.json')),'Previous start uncertain; inspect manually');
 const info=JSON.parse(d.docker(['info','--format','{{json .}}'],c));assert.equal(info.OSType,'linux');
 const stats=d.docker(['stats','--no-stream','--format','{{.MemUsage}}'],c).trim().split('\n').filter(Boolean);
 const used=stats.reduce((total,line)=>total+d.memoryBytes(line.split('/')[0]),0);
 const margin=info.MemTotal-used;
 console.log('Docker RAM: total '+(info.MemTotal/1024**3).toFixed(1)+' GiB; running containers '+(used/1024**3).toFixed(1)+' GiB; estimated headroom '+(margin/1024**3).toFixed(1)+' GiB');
 if(margin<7*1024**3)throw new environment.EnvironmentError('DEMO_MEMORY','Estimated free Docker memory below 7 GiB','Inspect docker stats and stop ONLY your own unneeded test environments by exact project after review. No automatic cleanup; this estimate is not a diagnosis of OOM.');
 for(const volume of [c.meta.mysqlVolume,c.meta.mongoVolume,c.meta.mongoConfigVolume])assert.equal(d.docker(['volume','ls','-q','--filter','name=^'+volume+'$'],c).trim(),'');
 const networkIds=d.docker(['network','ls','-q'],c).trim().split(/\s+/).filter(Boolean);
 if(networkIds.length)for(const n of JSON.parse(d.docker(['network','inspect',...networkIds],c)))for(const range of n.IPAM.Config||[])if(range.Subnet&&!range.Subnet.includes(':'))assert.ok(!d.overlap(c.meta.subnet,range.Subnet),'Demo subnet collision');
 assert.equal(d.docker(['network','ls','-q','--filter','name=^'+c.meta.network+'$'],c).trim(),'');
 await new Promise((resolve,reject)=>{const s=net.createServer();s.once('error',reject);s.listen(49080,'127.0.0.1',()=>s.close(resolve));});
 const ids=d.docker(['ps','-aq'],c).trim().split(/\s+/).filter(Boolean),before=snapshot(c,ids);
 const definition=d.definition(c),images={};
 for(const [name,s]of Object.entries(definition.services))images[name]=JSON.parse(d.docker(['image','inspect',s.image],c))[0].Id;
 fs.writeFileSync(path.join(d.runtime,'images.json'),JSON.stringify(images),{mode:0o600,flag:'wx'});
 d.compose(c,'config','--quiet');fs.writeFileSync(path.join(d.runtime,'start-intent.json'),JSON.stringify({project:c.meta.project,at:new Date().toISOString()}),{mode:0o600,flag:'wx'});
 console.log('Starting only new demo project '+c.meta.project);
 d.compose(c,'up','--detach','--no-build','--no-recreate','mysql','mongo');await wait(c,'mysql');await wait(c,'mongo');d.attest(c,['mysql','mongo']);
 d.compose(c,'up','--detach','--no-build','--no-deps','--no-recreate','identidad');await wait(c,'identidad');d.attest(c,['mysql','mongo','identidad']);
 const journal=new d.Journal(path.join(d.runtime,'provision-journal.json'));
 async function provision(key,values){await journal.step(key,async()=>{d.attest(c,['mysql','identidad']);const input=Object.entries(values).map(([k,v])=>k+'='+v).join('\n');d.composeInput(c,input,'run','--no-deps','--interactive','-T','--env','IDENTITY_PROVISION_FILE=-','identidad','provision');return {provisioned:true};});}
 for(const u of c.users.filter(u=>u.role!=='CLIENTE'))await provision('user-'+u.key,{operation:u.role==='ADMINISTRADOR'?'first-admin':'user',email:u.email,password:u.password,...(u.role==='ADMINISTRADOR'?{}:{role:u.role})});
 for(const [pair,scopes]of Object.entries(d.calls)){const [caller,target]=pair.split(':');await provision('client-'+caller+'-'+target,{operation:'client',caller,audience:'ticketya-'+target,scopes,secret:c.secrets[d.secretName(pair)]});}
 // Avoid overlapping four JVM cold starts/JIT peaks. No new memory thresholds.
 for(const n of ['eventos','inventario','entradas','compras']){d.compose(c,'up','--detach','--no-build','--no-deps','--no-recreate',n);await wait(c,n);}
 d.compose(c,'up','--detach','--no-build','--no-deps','--no-recreate','gateway');await wait(c,'gateway');d.attest(c);
 const response=await fetch('http://127.0.0.1:49080/api/v1/participants',{redirect:'error',signal:AbortSignal.timeout(15000)});assert.equal(response.status,200);assert.deepEqual(await response.json(),[]);
 assert.deepEqual(snapshot(c,ids),before,'A pre-existing container changed; stop for diagnosis');
 fs.writeFileSync(completed,JSON.stringify({project:c.meta.project,startedAt:new Date().toISOString()}),{mode:0o600,flag:'wx'});
 console.log('Demo ready at http://127.0.0.1:49080. No data loaded yet. Next: node demo/seed.cjs');
}
if(require.main===module)d.main(start);
