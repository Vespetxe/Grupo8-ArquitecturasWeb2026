'use strict';
// A fresh, disposable-in-purpose probe. Resources are stopped, never removed.
const assert=require('node:assert/strict'),crypto=require('node:crypto'),fs=require('node:fs'),path=require('node:path');
const {execFileSync}=require('node:child_process');
const guard=require('./central-isolation.cjs');
const id=crypto.randomBytes(8).toString('hex'),m=guard.identity(id,48080),project=`ticketya-e2-logs-probe-${id}`,logPort=49300+crypto.randomInt(600);
const dir=path.resolve(__dirname,'../target/observability',project);fs.mkdirSync(dir,{recursive:true});
const env={...process.env};for(const k of Object.keys(env))if(k.startsWith('COMPOSE_'))delete env[k];
function docker(args){return execFileSync('docker',args,{env,encoding:'utf8',stdio:['pipe','pipe','pipe'],maxBuffer:16*1024*1024});}
const file=path.join(dir,'compose.json'),collector=guard.spec(m).services.collector;
collector.ports[0].published=String(logPort);collector.environment={ARCHIVE_CAP_BYTES:'32768',ARCHIVE_SEGMENT_BYTES:'4096',ARCHIVE_TTL_SECONDS:'20'};
const program=`process.on('SIGTERM',()=>process.exit(0));const row={"@timestamp":"2026-11-14T00:00:00Z",service:{name:"probe"},log:{level:"INFO"},message:"safe",correlationId:"probe-cid",trace:{id:"probe-trace"},operationId:"probe:1"};let n=0;setInterval(()=>{for(let i=0;i<100;i++)console.log(JSON.stringify({...row,eventId:'probe-'+n++,message:'x'.repeat(700)}));},1000);`;
const spec={name:project,services:{collector,emitter:{image:'node:22.21.1-alpine',stop_grace_period:'1s',command:['node','-e',program],network_mode:'none',logging:guard.logging({...m,logPort})}},networks:{logs:{}},volumes:{logs_data:{}}};
fs.writeFileSync(file,JSON.stringify(spec,null,2));
function compose(...args){return docker(['compose','--env-file',process.platform==='win32'?'NUL':'/dev/null','-p',project,'-f',file,...args]);}
const pause=ms=>new Promise(r=>setTimeout(r,ms));
function inspect(n){return JSON.parse(docker(['inspect',`${project}-${n}-1`]))[0];}
function stats(){return JSON.parse(docker(['exec',`${project}-collector-1`,'python3','-c',"import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:9881/health').read().decode())"]));}
function rows(){return docker(['exec',`${project}-collector-1`,'python3','/collector/archive.py','--all','--limit','100000']).trim().split('\n').filter(Boolean).map(JSON.parse);}
async function wait(test,seconds=35){const until=Date.now()+seconds*1000;while(Date.now()<until){try{if(test())return;}catch{}await pause(500);}throw Error('Collector probe deadline');}
(async()=>{
 assert.equal(docker(['ps','-aq','--filter',`label=com.docker.compose.project=${project}`]).trim(),'');
 guard.assertEndpointFree(logPort,docker(['ps','-aq']).trim().split(/\s+/).filter(Boolean).map(id=>JSON.parse(docker(['inspect',id]))[0]));
 await new Promise((resolve,reject)=>{const s=require('node:net').createServer();s.once('error',reject);s.listen(logPort,'127.0.0.1',()=>s.close(resolve));});
 compose('config','--quiet');
 compose('up','-d','--no-build','--no-deps','emitter');await pause(1500);assert.equal(inspect('emitter').State.Running,true);
 assert.ok(docker(['logs',`${project}-emitter-1`]).includes('probe-cid'));
 compose('up','-d','--no-build','--no-deps','collector');await wait(()=>inspect('collector').State.Health?.Status==='healthy');
 await wait(()=>stats().accepted>0);
 await wait(()=>stats().evictedSegments>0);
 const before=stats();assert.ok(before.bytes<=32768);assert.ok(rows().every(r=>r.service.name==='probe'&&r.operationId==='probe:1'));
 compose('stop','collector');const countBefore=docker(['logs',`${project}-emitter-1`]).split('\n').length;await pause(2500);assert.equal(inspect('emitter').State.Running,true);assert.ok(docker(['logs',`${project}-emitter-1`]).split('\n').length>countBefore);
 compose('start','collector');await wait(()=>inspect('collector').State.Health?.Status==='healthy');
 await wait(()=>stats().accepted>0); // buffered Docker messages after recovery
 const recovered=stats();compose('stop','emitter');await pause(2500);
 const kept=rows();assert.ok(kept.length>0);compose('stop','collector');
 // Read the stopped collector's volume independently, then restart it. The
 // producer is now stopped so capacity eviction cannot invalidate the sample.
 const persisted=docker(['run','--rm','--network','none','--user','10001:10001','--mount',`type=volume,source=${project}_logs_data,target=/data,readonly`,'--entrypoint','python3','ticketya-e2-logs:dev','/collector/archive.py','--all','--limit','100000']);
 assert.ok(kept.some(r=>persisted.includes(r.eventId)));
 compose('start','collector');await wait(()=>inspect('collector').State.Health?.Status==='healthy');
 assert.ok(rows().some(r=>r.eventId===kept[0].eventId));
 await wait(()=>stats().bytes===0,35);const expired=stats();assert.ok(expired.expiredSegments>0);
 const report={result:'PASS',project,daemonLoopbackForward:true,dualLogging:true,startWithoutCollector:true,producerContinuedDuringOutage:true,persistentAfterStop:true,recoveredBufferedMessages:true,capacityBefore:before,afterRecovery:recovered,afterRetention:expired};
 fs.writeFileSync(path.join(dir,'report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));compose('stop');
})().catch(error=>{console.error(`Collector probe FAIL; inspect ${project} and ${dir}; resources preserved`);console.error(String(error.stderr||error.message));process.exitCode=1;});
