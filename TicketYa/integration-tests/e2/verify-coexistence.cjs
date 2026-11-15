'use strict';
// Minimal technical proof. No application replicas, financial operations or existing-resource mutations.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const g=require('./mongo-central-isolation.cjs');const env={...process.env};for(const k of Object.keys(env))if(k.startsWith('COMPOSE_')||k.startsWith('TY_REG_'))delete env[k];
const docker=(args,input)=>execFileSync('docker',args,{input,env,encoding:'utf8',timeout:30000,maxBuffer:8*1024*1024,stdio:['pipe','pipe','pipe']});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function main(){
 const ids=docker(['ps','-aq']).trim().split(/\s+/).filter(Boolean);
 const format='{{.Id}}|{{.State.Status}}|{{.State.StartedAt}}|{{.RestartCount}}';
 const snapshot=()=>docker(['inspect','--format',format,...ids]);const before=snapshot();
 const configs=docker(['inspect','--format','{{json .HostConfig.LogConfig}}',...ids]).trim().split(/\r?\n/).map(JSON.parse);
 const networks=docker(['network','ls','-q']).trim().split(/\s+/),networkState=JSON.parse(docker(['network','inspect',...networks]));
 let meta;for(let port=48080;port<=48089;port++){const candidate=g.identity(crypto.randomBytes(8).toString('hex'),port);try{g.assertEndpointFree(candidate.logPort,configs.map(LogConfig=>({HostConfig:{LogConfig}})));g.assertSubnetFree(candidate,networkState);await new Promise((resolve,reject)=>{const server=require('node:net').createServer();server.once('error',reject);server.listen(candidate.logPort,'127.0.0.1',()=>server.close(resolve));});meta=candidate;break;}catch{}}
 assert.ok(meta,'No unused canonical collector endpoint; no resources created');
 const spec=g.spec(meta);g.validate(meta,spec);
 for(const service of Object.values(spec.services))for(const value of Object.values(service.environment||{}))for(const match of value.matchAll(/\$\{(TY_REG_[A-Z_]+):/g))env[match[1]]??=crypto.randomBytes(32).toString('hex');
 fs.mkdirSync(meta.directory,{recursive:true,mode:0o700});if(process.platform==='win32'){const account=execFileSync('whoami',[],{encoding:'utf8'}).trim();execFileSync('icacls.exe',[meta.directory,'/inheritance:r','/grant:r',account+':(OI)(CI)F'],{stdio:'pipe'});}
 const file=path.join(meta.directory,'coexistence-compose.json');fs.writeFileSync(file,JSON.stringify(spec,null,2));
 const compose=(...args)=>{g.validate(meta,JSON.parse(fs.readFileSync(file)));return docker(['compose','--env-file',process.platform==='win32'?'NUL':'/dev/null','--project-name',meta.project,'--file',file,...args]);};
 compose('config','--quiet');compose('up','--detach','--no-build','--no-deps','mongo','collector');
 for(const name of ['mongo','collector']){let healthy=false;for(let i=0;i<45;i++){const c=JSON.parse(docker(['inspect',`${meta.project}-${name}-1`]))[0];assert.equal(c.Config.Labels['com.docker.compose.project'],meta.project);if(c.State.Health?.Status==='healthy'){healthy=true;break;}assert.notEqual(c.State.Status,'exited');await sleep(1000);}assert.ok(healthy,'Readiness deadline');}
 docker(['exec',meta.project+'-mongo-1','mongosh','--quiet','--file','/opt/ticketya/healthcheck.js']);
 const correlationId=crypto.randomUUID();const record={'@timestamp':new Date().toISOString(),service:{name:'ticketya-coexistence-probe'},log:{level:'INFO',logger:'coexistence'},message:'Technical coexistence verification; no business operation',eventType:'INTEGRATION_PROBE',correlationId};
 const options=Object.entries(g.logging(meta).options).flatMap(([k,v])=>['--log-opt',k+'='+v]);
 const probe=meta.project+'-technical-probe';docker(['run','--detach','--name',probe,'--label','com.docker.compose.project='+meta.project,'--network',meta.network,'--log-driver','fluentd',...options,'node:22.21.1-alpine','node','-e',`console.log(${JSON.stringify(JSON.stringify(record))});setTimeout(()=>{},30000)`]);
 let rows=[];for(let i=0;i<30;i++){rows=docker(['exec',meta.project+'-collector-1','python3','/collector/archive.py','--query','correlationId','--value',correlationId]).trim().split('\n').filter(Boolean).map(JSON.parse);if(rows.length)break;await sleep(1000);}assert.equal(rows.length,1);assert.equal(rows[0].eventType,'INTEGRATION_PROBE');
 assert.ok(docker(['logs',probe]).includes(correlationId));assert.equal(snapshot(),before,'Previous containers changed');
 const result={result:'PASS',project:meta.project,composeServices:9,started:['mongo','collector','technical-probe'],mongoAuthenticated:true,collectorHealthy:true,archivedRecords:rows.length,dockerLogsCompatible:true,previousContainersUnchanged:true,fullNineServiceCampaignRepeated:false};
 fs.writeFileSync(path.join(meta.directory,'coexistence-report.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
}
main().catch(error=>{let message=String(error.stderr||error.message);for(const [key,value]of Object.entries(env))if(key.startsWith('TY_REG_')&&value)message=message.replaceAll(value,'[REDACTED]');console.error('Coexistence proof failed; retained isolated resources; '+message.slice(-1200));process.exitCode=1;});
