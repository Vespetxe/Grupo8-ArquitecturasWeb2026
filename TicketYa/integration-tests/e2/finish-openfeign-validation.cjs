'use strict';
// Bounded continuation of the interrupted test tail. Never repeats the 455 commercial requests.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../../..');let secrets=[],currentReportFile;
async function main(){
 assert.equal(process.argv[2],'--run');assert.match(process.argv[3]||'',/^[a-f0-9]{16}$/);const resumeBounded=process.argv[4]==='--resume-bounded';assert.equal(process.argv.length,resumeBounded?5:4);
 const directory=path.join(__dirname,'../target/security',process.argv[3]);
 const original=JSON.parse(fs.readFileSync(path.join(directory,'report.json')));
 assert.equal(original.result,'FAIL');assert.equal(original.stage,'commercial HTTP');assert.match(original.diagnostic,/spawnSync docker ETIMEDOUT/);
 const privateState=JSON.parse(fs.readFileSync(path.join(directory,'newman-private-context.json'))),{meta,adminPassword,provisioned}=privateState;
 assert.equal(meta.id,process.argv[3]);assert.equal(meta.project,'ticketya-e2-reg-'+meta.id);
 const spec=JSON.parse(fs.readFileSync(path.join(directory,'compose.json')));
 const tag=spec.services.eventos.image.split(':')[1];assert.match(tag,/^feign-[a-f0-9]{8}$/);process.env.TY_FEIGN_IMAGE_TAG=tag;
 const guard=require('./recommendation-isolation.cjs');guard.validate(meta,spec);
 const env={...process.env};for(const key of Object.keys(env))if(key.startsWith('COMPOSE_'))delete env[key];
 function docker(args,input,extra={},timeout=30000){return cp.execFileSync('docker',args,{cwd:root,env:{...env,...extra},input,encoding:'utf8',stdio:['pipe','pipe','pipe'],maxBuffer:32*1024*1024,timeout});}
 const inspect=name=>JSON.parse(docker(['inspect',meta.project+'-'+name+'-1']))[0];
 const entries=Object.fromEntries(Object.keys(spec.services).map(name=>[name,inspect(name)]));
 const vars=c=>Object.fromEntries(c.Config.Env.map(v=>[v.slice(0,v.indexOf('=')),v.slice(v.indexOf('=')+1)]));
 const credentials={TY_REG_ROOT_PASSWORD:vars(entries.mysql).MYSQL_ROOT_PASSWORD};
 for(const name of ['eventos','inventario','entradas','compras','identidad'])credentials['TY_REG_'+name.toUpperCase()+'_PASSWORD']=vars(entries[name]).DB_PASSWORD;
 const mongo=vars(entries.mongo);credentials.TY_REG_MONGO_ROOT_PASSWORD=mongo.MONGO_INITDB_ROOT_PASSWORD;credentials.TY_REG_MONGO_EVENTOS_PASSWORD=mongo.TY_E2_EVENTOS_MONGO_PASSWORD;
 for(const[caller,targets]of Object.entries(guard.CALLS))for(const target of targets)credentials['TY_REG_'+caller.toUpperCase()+'_'+target.toUpperCase()+'_SECRET']=vars(entries[caller])['TY_SECURITY_'+target.toUpperCase()+'_SECRET'];
 secrets=[...Object.values(credentials),adminPassword,...provisioned.map(p=>p.password)].filter(Boolean);
 const images=Object.fromEntries(Object.keys(spec.services).map(name=>[name,JSON.parse(docker(['image','inspect',spec.services[name].image]))[0].Id]));
 function attest(){for(const name of Object.keys(spec.services))guard.attest(meta,name,inspect(name),credentials,images);}
 function sql(query){assert(/^SELECT\s/i.test(query.trim()));assert(!query.replace(/;\s*$/,'').includes(';'));attest();return docker(['exec','-i',meta.project+'-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --batch --skip-column-names --raw'],query+'\n').trim();}
 attest();fs.writeFileSync(path.join(directory,resumeBounded?'continuation-resume-bounded-started.json':'continuation-started.json'),JSON.stringify({started:new Date().toISOString(),source:'report.json',originalPreserved:true}),{mode:0o600,flag:'wx'});
 const result=resumeBounded?JSON.parse(fs.readFileSync(path.join(directory,'continuation-report.json'))):{project:meta.project,result:'RUNNING',originalCampaignPreserved:true,commercialRequestsNotRepeated:original.commercial.requests.length};
 if(resumeBounded){assert.equal(result.stage,'bounded load');assert.notEqual(result.result,'PASS');result.result='RUNNING';}
 currentReportFile=path.join(directory,'continuation-report.json');
 const save=stage=>{result.stage=stage;fs.writeFileSync(path.join(directory,'continuation-report.json'),JSON.stringify(result,null,2));console.log(stage);};
 const outputDir=path.join(directory,resumeBounded?'continuation-newman-resumed':'continuation-newman');fs.mkdirSync(outputDir,{recursive:true,mode:0o700});
 const transport=require('./newman-transport.cjs').create({directory:outputDir,runId:meta.id+'-tail'}),requests=[];
 async function api(method,route,body,expected=200,actor,extra={},check=true){if(check)attest();const started=performance.now();const response=await transport.request({method,url:'http://127.0.0.1:'+meta.port+route,body,expected,headers:{...extra,...(actor?{Authorization:'Bearer '+actor.token}:{})}});requests.push({method,path:route,status:response.status,expected,correlationId:response.correlationId,durationMs:performance.now()-started,actor:actor?.id});return Array.isArray(expected)?response:response.body;}
 async function login(user){const p=await api('POST','/auth/login',{email:user.email,password:user.password},200);secrets.push(p.access_token);const actor={...user,token:p.access_token};const me=await api('GET','/auth/me',undefined,200,actor);return {...actor,id:me.id,roles:me.roles};}
 let admin,access;let organizers=[],clients=[];
 if(resumeBounded){const saved=JSON.parse(fs.readFileSync(path.join(directory,'continuation-private-context.json')));admin=await login(saved.admin);access=await login(saved.access);for(const actor of saved.organizers)organizers.push(await login(actor));for(const actor of saved.clients)clients.push(await login(actor));}else{
 admin=await login({email:'admin-'+meta.id+'@example.test',password:adminPassword});assert(admin.roles.includes('ADMINISTRADOR'));
 save('recovering controller probe evidence');
 const probes=JSON.parse(docker(['logs',meta.project+'-newman-probes']).trim());assert.equal(probes.requests,100);
 fs.writeFileSync(path.join(directory,'newman-controller-probes-original.json'),JSON.stringify(probes,null,2));
 assert.equal(probes.failures.length,2);assert(probes.failures.every(f=>/eventos (PUT|DELETE) \/api\/v1\/participants/.test(f.name)));
 for(const method of ['PUT','DELETE'])await api(method,'/api/v1/participants/999999999',method==='PUT'?{}:undefined,401);
 result.controllerProbes={originalRequests:100,originalAssertions:probes.assertions,originalExpectationErrors:2,focusedCorrections:2,controllersCovered:87,technicalTokenRequests:7,technicalReadinessRequests:4,administrativeOpenApiRequests:2};
 save('verifying traces and committed audit before additional finance');
 const logs=Object.fromEntries(['gateway','identidad','eventos','inventario','compras','entradas'].map(name=>[name,docker(['logs',entries[name].Id])]));
 result.tracing=require('./verify-tracing.cjs')({logs,requests:original.commercial.requests,secrets});
 const ids=docker(['ps','-aq','--filter','label=com.docker.compose.project='+meta.project]).trim().split(/\s+/);
 const provisioning=JSON.parse(docker(['inspect',...ids])).filter(c=>c.Name.includes('-identidad-run-')).map(c=>docker(['logs',c.Id])).join('\n');
 const commercial={...original.commercial,identities:{admin:admin.id}};
 result.audit=require('./verify-audit.cjs').verify({logs:{...logs,provision:provisioning},secrets,commercial,meta,sql});
 save('preparing only remaining load and frontend fixtures');
 for(const user of provisioned){const actor=await login(user);if(user.role==='ORGANIZADOR')organizers.push(actor);else access=actor;}
 for(let i=0;i<3;i++){const user={email:'tail-'+i+'-'+meta.id+'@example.test',password:crypto.randomBytes(32).toString('hex')};await api('POST','/auth/register',user,201);clients.push(await login(user));secrets.push(user.password);}
 fs.writeFileSync(path.join(directory,'continuation-private-context.json'),JSON.stringify({meta,clients,organizers,access,admin}),{mode:0o600,flag:'wx'});
 }
 async function event(numbered,price,label){assert.equal(numbered,false);const name='Feign tail '+label+' '+meta.id;const v=await api('POST','/api/v1/venues',{name,address:'Isolated test',city:'Tandil',latitude:-37.328,longitude:-59.137},201,organizers[0]);const s=await api('POST','/api/v1/venues/'+v.venueId+'/sectors',{name:'General',type:'NO_NUMERADO',capacity:2},201,organizers[0]);const e=await api('POST','/api/v1/events',{name,eventType:'Recital',organizerId:organizers[0].id,venueId:v.venueId,startDateTime:new Date(Date.now()+8*86400000).toISOString(),participantIds:[]},201,organizers[0]);await api('POST','/api/v1/events/'+e.eventId+'/prices',[{sectorId:s.sectorId,price}],201,organizers[0]);assert.equal((await api('PATCH','/api/v1/events/'+e.eventId+'/publish',undefined,200,organizers[0])).status,'PUBLICADO');return {venue:v.venueId,sector:s.sectorId,event:e.eventId,price,numbered:false};}
 const context={meta,sql,attest,api,clients,organizers,admin,access,event,requests};
 save('bounded load');result.load=await require('./load-smoke.cjs')(context);
 save('real frontend QR and original payment recovery');result.frontend=await require('./frontend-delivery.cjs')({meta,context,load:result.load,provisioned});
 save('standalone Postman Collection Runner flow');result.newmanFlows=require('./newman-flows-run.cjs')({meta,docker,adminPassword,provisioned,sql});
 result.newman=transport.finish();
 save('final read-only integrity');
 for(const[table,key,schema]of [['pagos','compra_id','compras'],['ticket_batches','order_id','entradas']])assert.equal(sql(`SELECT COUNT(*) FROM (SELECT ${key} FROM ${meta.schemas[schema]}.${table} GROUP BY ${key} HAVING COUNT(*)>1) duplicates;`),'0');
 result.noDuplicatePaymentsOrBatches=true;attest();result.result='PASS';save('complete');
}
main().catch(error=>{let message=String(error.stderr||error.message);for(const s of secrets)message=message.replaceAll(s,'[REDACTED]');if(currentReportFile){const report=JSON.parse(fs.readFileSync(currentReportFile));report.result='FAIL';report.diagnostic=message.slice(-1000);fs.writeFileSync(currentReportFile,JSON.stringify(report,null,2));}console.error('Continuation stopped: '+message.replace(/eyJ[\w-]+\.[\w-]+\.[\w-]+/g,'[TOKEN]').slice(-2000));process.exitCode=1;});
