'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {execFileSync}=require('node:child_process');
const environment=require('./environment.cjs');
const newmanRequested=process.argv.includes('--newman');
const central=process.argv.includes('--centralized');
const loadRequested=process.argv.includes('--load');
const frontend=process.argv.includes('--frontend');
assert.ok(!frontend||loadRequested,'Frontend delivery check requires the isolated load tickets');
let loadContext;
const cache=process.argv.includes('--cache');
const reports=process.argv.includes('--reports');
const notifications=process.argv.includes('--notifications');
const recommendations=process.argv.includes('--recommendations')||notifications||reports;
const mongoTopology=recommendations||cache;
const guard=require(mongoTopology&&central?'./mongo-central-isolation.cjs':mongoTopology?'./recommendation-isolation.cjs':central?'./central-isolation.cjs':'./security-isolation.cjs');
const root=path.resolve(__dirname,'../../..');
let stage='preflight';
const commercialOnly=process.argv.includes('--commercial-only');
const faults=process.argv.includes('--faults');
const queue=process.argv.includes('--queue');
const qr=process.argv.includes('--qr'),qrSecrets=[];
const args=process.argv.slice(2).filter(arg=>!['--newman','--commercial-only','--faults','--queue','--qr','--recommendations','--notifications','--reports','--cache','--load','--frontend'].includes(arg));if(central)args.splice(args.indexOf('--centralized'),1);const audit=args.includes("--audit")||central;if(args.includes('--audit'))args.splice(args.indexOf("--audit"),1);const observe=args.includes("--observability")||audit;if(args.includes("--observability"))args.splice(args.indexOf("--observability"),1);assert.ok(args.length===0||(args.length===2&&args[0]==='--port'));
assert.ok(!commercialOnly||!observe,'Commercial-only skips the already approved security/observability suites, never application security');
let meta=guard.identity(crypto.randomBytes(8).toString('hex'),args.length?Number(args[1]):48082);
const env={...process.env};for(const k of Object.keys(env))if(k.startsWith('COMPOSE_')||k.startsWith('TY_REG_'))delete env[k];
const credentials={};const provisionLogs=[];
for(const name of ['ROOT','EVENTOS','INVENTARIO','ENTRADAS','COMPRAS','IDENTIDAD',...(mongoTopology?['MONGO_ROOT','MONGO_EVENTOS']:[])])credentials[`TY_REG_${name}_PASSWORD`]=crypto.randomBytes(32).toString('hex');
for(const [caller,targets]of Object.entries(guard.CALLS))for(const target of targets)credentials[`TY_REG_${caller.toUpperCase()}_${target.toUpperCase()}_SECRET`]=crypto.randomBytes(32).toString('hex');
Object.assign(env,credentials);
let file=path.join(meta.directory,'compose.json');
let engineFault=false;
const report={project:meta.project,run:meta.id,result:'RUNNING',securityChecksSkipped:commercialOnly,requests:[]};
function docker(args,input,extra={},timeout=30000){try{return execFileSync('docker',args,{env:{...env,...extra},input,encoding:'utf8',stdio:['pipe','pipe','pipe'],maxBuffer:32*1024*1024,timeout});}catch(error){if((error.code==='ETIMEDOUT'&&!args.includes('/network.cjs'))||/Internal Server Error|500 Internal|cannot connect|is the docker daemon running/i.test(String(error.stderr)))engineFault=true;throw error;}}
function composeInput(input,...args){guard.validate(meta,JSON.parse(fs.readFileSync(file)));return docker(['compose','--env-file',process.platform==='win32'?'NUL':'/dev/null','--project-name',meta.project,'--file',file,...args],input);}
function compose(...args){return composeInput(undefined,...args);}
function inspect(name){return JSON.parse(docker(['inspect',`${meta.project}-${name}-1`]))[0];}
const images={};
const expectedStopped=new Set();
const faultSecrets=[];
function attest(names){guard.validate(meta,JSON.parse(fs.readFileSync(file)));for(const name of names){const c=inspect(name);if(expectedStopped.has(name)){assert.equal(c.Config.Labels['com.docker.compose.project'],meta.project);assert.equal(c.Image,images[name]);assert.equal(c.State.Running,false);}else guard.attest(meta,name,c,credentials,images);}}
function sql(query){attest(['mysql']);return docker(['exec','-i',`${meta.project}-mysql-1`,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --batch --skip-column-names --raw'],query+'\n').trim();}
async function wait(name){const deadline=Date.now()+180000;while(Date.now()<deadline){const c=inspect(name);if(c.State.Status==='exited'||c.State.Status==='dead')throw environment.startupFailure(name,c);if(['mysql','collector','mongo'].includes(name)?c.State.Health?.Status==='healthy':docker(['logs','--since',c.State.StartedAt,c.Id]).includes('Started '))return;await new Promise(r=>setTimeout(r,2000));}throw environment.startupDeadline(name);}
// Fault controls address only containers created by THIS run.
const controls={
 available(){return !engineFault;},
 async beforeFaults(){
  if(!central)return;
  compose('start','collector');await wait('collector');
  const logs=Object.fromEntries(['gateway','identidad','eventos','inventario','compras','entradas'].map(name=>[name,docker(['logs',inspect(name).Id])]));
  report.centralBeforeFaults=await require('./verify-central.cjs')({docker,container:inspect('collector').Id,logs,secrets:[...Object.values(credentials),...faultSecrets],requests:report.commercial.requests});
  if(mongoTopology){report.centralBeforeFaults.commercialDuringCollectorOutage=false;report.centralBeforeFaults.recoveredWithoutApplicationRestart=false;}
 },
 pausePurchases(){attest(['compras']);compose('pause','compras');assert.equal(inspect('compras').State.Paused,true);},
 resumePurchases(){compose('unpause','compras');attest(['compras']);},
 stop(name){assert.ok(['compras','identidad',...(recommendations?['mongo']:[])].includes(name));attest([name]);compose('stop','--timeout','15',name);assert.equal(inspect(name).State.Running,false);expectedStopped.add(name);},
 async start(name){assert.ok(expectedStopped.has(name));compose('start',name);await wait(name);expectedStopped.delete(name);attest([name]);},
 entries(){return docker(['logs',inspect('entradas').Id]).split('\n').filter(l=>l.startsWith('{')).map(l=>JSON.parse(l));}
};
// Only the newly created canonical project may be stopped or queried here.
const recommendationControls={
 available(){return controls.available();},
 stop(name){assert.ok(recommendations&&['compras','mongo'].includes(name));controls.stop(name);},
 async start(name){assert.ok(recommendations&&['compras','mongo'].includes(name));await controls.start(name);},
 mongoDocuments(){attest(['mongo']);const program='const c=db.getSiblingDB(process.env.MONGO_INITDB_DATABASE);if(!c.auth("ty_eventos_preferences",process.env.TY_E2_EVENTOS_MONGO_PASSWORD))quit(1);print(JSON.stringify(c.preferences.find({}).sort({_id:1}).toArray()));';return JSON.parse(docker(['exec','-i',inspect('mongo').Id,'mongosh','--quiet','--file','/dev/stdin'],program));}
};
function snapshot(ids){return ids.map(id=>{const c=JSON.parse(docker(['inspect',id]))[0];return {id:c.Id,state:c.State.Status,start:c.State.StartedAt,finish:c.State.FinishedAt,restarts:c.RestartCount};});}
async function main(){
 environment.nodeVersion();report.tools=environment.engine(docker);
 if(frontend){environment.frontend();await environment.portFree(5174);}
 const availableImages=environment.images(docker,[...Object.values(guard.spec(meta).services).map(s=>s.image),...(!commercialOnly?['node:22.21.1-alpine']:[])]);
 if(mongoTopology){const ids=docker(['network','ls','-q']).trim().split(/\s+/).filter(Boolean);meta=environment.chooseMeta(guard,meta,ids.length?JSON.parse(docker(['network','inspect',...ids])):[]);file=path.join(meta.directory,'compose.json');Object.assign(report,{project:meta.project,run:meta.id});}
 const spec=guard.spec(meta);guard.validate(meta,spec);
 const oldIds=docker(['ps','-aq']).trim().split(/\s+/).filter(Boolean),previous=snapshot(oldIds);
 if(central)guard.assertEndpointFree(meta.logPort,oldIds.map(id=>JSON.parse(docker(['inspect',id]))[0]));
 assert.equal(docker(['ps','-aq','--filter',`label=com.docker.compose.project=${meta.project}`]).trim(),'');
 assert.equal(docker(['volume','ls','-q','--filter',`name=^${meta.volume}$`]).trim(),'');
 if(mongoTopology){assert.equal(docker(['volume','ls','-q','--filter',`name=^${meta.mongoVolume}$`]).trim(),'');const ids=docker(['network','ls','-q']).trim().split(/\s+/).filter(Boolean);guard.assertSubnetFree(meta,JSON.parse(docker(['network','inspect',...ids])));}
 if(central){assert.equal(docker(['volume','ls','-q','--filter',`name=^${meta.logVolume}$`]).trim(),'');await environment.portFree(meta.logPort);}
 // Check the only host port before creating any resources.
 await environment.portFree(meta.port);
 fs.mkdirSync(path.join(meta.directory,'keys'),{recursive:true,mode:0o700});
 if(process.platform==='win32'){const account=execFileSync('whoami',[],{encoding:'utf8'}).trim();execFileSync('icacls.exe',[meta.directory,'/inheritance:r','/grant:r',`${account}:(OI)(CI)F`],{stdio:'pipe'});}
 const pair=crypto.generateKeyPairSync('rsa',{modulusLength:3072,privateKeyEncoding:{type:'pkcs8',format:'pem'},publicKeyEncoding:{type:'spki',format:'pem'}});
 fs.writeFileSync(path.join(meta.directory,'keys/private.pem'),pair.privateKey,{mode:0o600,flag:'wx'});fs.writeFileSync(path.join(meta.directory,'keys/public.pem'),pair.publicKey,{mode:0o644,flag:'wx'});
 if(process.platform!=='win32'){fs.chmodSync(path.join(meta.directory,'keys'),0o755);fs.chmodSync(path.join(meta.directory,'keys/private.pem'),0o444);}
 fs.writeFileSync(file,JSON.stringify(spec,null,2));
 for(const [name,s]of Object.entries(spec.services))images[name]=availableImages[s.image];
 stage='compose validation';compose('config','--quiet');
 stage='mysql startup';compose('up','--detach','--no-build','mysql');await wait('mysql');attest(['mysql']);
 if(mongoTopology){stage='isolated Mongo startup';compose('up','--detach','--no-build','mongo');await wait('mongo');attest(['mongo']);}
 stage='isolated schemas';
 for(const [name,schema]of Object.entries(meta.schemas)){
  const escaped=schema.replaceAll('_','\\_');
  sql(`SET SESSION sql_mode='NO_BACKSLASH_ESCAPES'; CREATE DATABASE \`${schema}\`; CREATE USER 'reg_${name}'@'%' IDENTIFIED BY '${credentials[`TY_REG_${name.toUpperCase()}_PASSWORD`]}'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON \`${escaped}\`.* TO 'reg_${name}'@'%';`);
 }
 docker(['exec','-i','--env','IDENTITY_SCHEMA','--env','TY_E2_IDENTIDAD_DB_PASSWORD',`${meta.project}-mysql-1`,'bash','-s','--','--isolated'],fs.readFileSync(path.join(root,'docker/mysql/create-identity.sh'),'utf8').replace(/\r/g,''),{IDENTITY_SCHEMA:meta.identitySchema,TY_E2_IDENTIDAD_DB_PASSWORD:credentials.TY_REG_IDENTIDAD_PASSWORD});
 stage='identity startup';compose('up','--detach','--no-build','--no-deps','identidad');await wait('identidad');attest(['mysql','identidad']);
 function provision(values){attest(['mysql','identidad']);const input=Object.entries(values).map(([k,v])=>`${k}=${v}`).join('\n');provisionLogs.push(composeInput(input,'run','--no-deps','--interactive','-T','--env','IDENTITY_PROVISION_FILE=-','identidad','provision'));}
 const adminPassword=crypto.randomBytes(32).toString('hex');const adminPasswordForFaults=adminPassword;faultSecrets.push(adminPassword);provision({operation:'first-admin',email:`admin-${meta.id}@example.test`,password:adminPassword});
 const provisioned=[];
 for(const [name,role]of [['organizer-a','ORGANIZADOR'],['organizer-b','ORGANIZADOR'],['access','PERSONAL_ACCESO']]){const user={email:name+'-'+meta.id+'@example.test',password:crypto.randomBytes(32).toString('hex'),role};faultSecrets.push(user.password);provision({operation:'user',...user});provisioned.push(user);}
 const scopes=guard.SCOPES;
 for(const [caller,targets]of Object.entries(guard.CALLS))for(const target of targets)provision({operation:'client',caller,audience:'ticketya-'+target,scopes:scopes[caller+':'+target],secret:credentials[`TY_REG_${caller.toUpperCase()}_${target.toUpperCase()}_SECRET`]});
 faultSecrets.push(adminPassword,...provisioned.map(u=>u.password));
 // Private checkpoint in the already ACL-restricted/ignored run directory; never printed/exported.
 if(newmanRequested)fs.writeFileSync(path.join(meta.directory,'newman-private-context.json'),JSON.stringify({meta,adminPassword,provisioned}),{mode:0o600,flag:'wx'});
 stage='business service startup';compose('up','--detach','--no-build','--no-deps','eventos','inventario','entradas','compras');
 for(const name of ['eventos','inventario','entradas','compras'])await wait(name);
 stage='gateway startup';compose('up','--detach','--no-build','--no-deps','gateway');await wait('gateway');
 if(central){stage='collector startup after applications';compose('up','--detach','--no-build','--no-deps','collector');await wait('collector');}
 stage='attestation';attest(Object.keys(spec.services));assert.equal(docker(['ps','-q','--filter',`label=com.docker.compose.project=${meta.project}`]).trim().split(/\s+/).length,Object.keys(spec.services).length);
 if(!commercialOnly){
 stage='security HTTP';
 const mounts=['security-smoke.cjs','security-isolation.cjs','isolation.cjs'].flatMap(name=>['--mount',`type=bind,source=${path.join(__dirname,name)},target=/${name},readonly`]);
 const output=docker(['run','--name',meta.project+'-security-probe','--label','com.docker.compose.project='+meta.project,'--log-driver','none','--network',meta.network,...mounts,'--mount',`type=bind,source=${path.join(meta.directory,'keys')},target=/keys,readonly`,
  '--env','RUN_ID','--env','ADMIN_PASSWORD',...Object.keys(credentials).flatMap(k=>['--env',k]),'node:22.21.1-alpine','node','/security-smoke.cjs'],undefined,{RUN_ID:meta.id,ADMIN_PASSWORD:adminPassword});
 Object.assign(report,JSON.parse(output));
 }
 if(central&&!mongoTopology){stage='collector outage';compose('stop','collector');assert.equal(inspect('collector').State.Running,false);}
 stage='commercial HTTP';
 const newmanTransport=newmanRequested?require('./newman-transport.cjs').create({directory:meta.directory,runId:meta.id}):null;
 report.commercial=await require('./commercial-smoke.cjs')({transport:newmanTransport?.request,finalValidation:newmanTransport?context=>{report.newmanControllers=require("./newman-coverage.cjs")({...context,meta,credentials,docker});}:undefined,meta,adminPassword,provisioned,queue,qr,notifications,reports,cache,prepareLoad:loadRequested?context=>{loadContext=context;}:undefined,recommendations:recommendations?recommendationControls:undefined,rememberQr:tokens=>qrSecrets.push(...tokens),faults:faults?controls:undefined,progress:partial=>{report.commercial=partial;fs.writeFileSync(path.join(meta.directory,'report.json'),JSON.stringify(report,null,2));},attest:()=>attest(Object.keys(spec.services).filter(n=>n!=='collector')),sql:query=>{assert.ok(/^SELECT\s/i.test(query.trim())&&!query.replace(/;\s*$/,'').includes(';'),'Commercial checks are read-only SQL');return sql(query);}});
 if(central){stage='collector recovery';if(!inspect('collector').State.Running)compose('start','collector');await wait('collector');}
 if(observe){
  stage='tracing verification';
  await new Promise(r=>setTimeout(r,500));
  const logs=Object.fromEntries(['gateway','identidad','eventos','inventario','compras','entradas'].map(name=>[name,docker(['logs',inspect(name).Id])]));
  report.tracing=require('./verify-tracing.cjs')({logs,requests:report.commercial.requests,secrets:[...Object.values(credentials),adminPassword,...provisioned.map(u=>u.password),...qrSecrets]});
  if(audit){stage='audit verification';report.audit=require('./verify-audit.cjs').verify({progress:step=>{report.auditCheck=step;},logs:{...logs,provision:provisionLogs.join('\n')},secrets:[...Object.values(credentials),adminPassword,...provisioned.map(u=>u.password),...qrSecrets],commercial:report.commercial,meta,sql:query=>{assert.ok(/^SELECT\s/i.test(query.trim())&&!query.replace(/;\s*$/,'').includes(';'));return sql(query);}});}
  if(central){stage='central archive verification';report.central=await require('./verify-central.cjs')({docker,container:inspect('collector').Id,logs,secrets:[...Object.values(credentials),adminPassword,...provisioned.map(u=>u.password),...qrSecrets],requests:report.commercial.requests});if(mongoTopology){report.central.commercialDuringCollectorOutage=false;report.central.recoveredWithoutApplicationRestart=false;}}
 }
 if(mongoTopology){const text=docker(['logs',inspect('mongo').Id]);for(const secret of Object.values(credentials))assert.ok(!text.includes(secret),'Credential found in Mongo logs');report.mongoLogsRedacted=true;}
 if(loadRequested){stage='bounded purchase load';const commercial=report.commercial;report.load=await require('./load-smoke.cjs')(loadContext);report.commercial=commercial;}
 if(frontend){stage='frontend accepted QR';const commercial=report.commercial;report.frontend=await require('./frontend-delivery.cjs')({meta,context:loadContext,load:report.load,provisioned});report.commercial=commercial;}
 if(newmanRequested){stage='standalone Newman flow';report.newmanFlows=require('./newman-flows-run.cjs')({meta,docker,adminPassword,provisioned,sql,includeAdditional:true});}
 stage='final checks';attest(Object.keys(spec.services));assert.deepEqual(snapshot(oldIds),previous);
 assert.equal(sql(`SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN (${Object.values(meta.schemas).map(s=>"'"+s+"'").join(',')});`),'30');
 if(newmanTransport)report.newman=newmanTransport.finish();
 Object.assign(report,{result:'PASS',services:Object.keys(spec.services).length,schemas:5,previousContainersUnchanged:true,images});
 fs.writeFileSync(path.join(meta.directory,'report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
}
main().catch(error=>{report.result='FAIL';let diagnostic=String(error.stderr||error.message||'');for(const secret of [...Object.values(credentials),...faultSecrets,...qrSecrets])diagnostic=diagnostic.replaceAll(secret,'[REDACTED]');report.diagnostic=diagnostic.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[REDACTED_TOKEN]').slice(-2000);report.stage=stage;report.assertion=error.code==='ERR_ASSERTION'?{code:error.code,operator:error.operator,location:String(error.stack).split('\n').find(l=>l.includes('.cjs:'))?.trim()}:undefined;
 const controlled=error instanceof environment.EnvironmentError;if(controlled){report.errorCode=error.code;report.hint=error.hint;}try{fs.mkdirSync(meta.directory,{recursive:true,mode:0o700});fs.writeFileSync(path.join(meta.directory,'report.json'),JSON.stringify(report,null,2),{mode:0o600});}catch{}
 const safe=String(error.stderr||'').match(/Security HTTP check failed: ([^\r\n]+)/);console.error(`Security run failed at ${stage}; project=${meta.project}`+(safe?' '+safe[1]:''));if(controlled)console.error(error.code+': '+error.message+'\n'+error.hint);else if(stage==='preflight')console.error('Revisar imágenes con environment.cjs --all --frontend y colisiones del puerto Gateway/Forward. Elegir otro --port 48080–48089; no borrar recursos anteriores.');console.error('Diagnóstico protegido: '+path.relative(root,path.join(meta.directory,'report.json')));process.exitCode=1;});
