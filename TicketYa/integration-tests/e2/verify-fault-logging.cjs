'use strict';
// Targeted delivery check after a campaign: no commercial writes and no replay of old logs.
const fs=require('node:fs'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const {execFileSync}=require('node:child_process'),guard=require('./central-isolation.cjs'),{urlFor}=require('./isolation.cjs');
const meta=guard.identity(process.argv[2],Number(process.argv[3]));
const file=require('node:path').join(meta.directory,'compose.json'),spec=JSON.parse(fs.readFileSync(file));guard.validate(meta,spec);
const previous=JSON.parse(fs.readFileSync(require('node:path').join(meta.directory,'report.json')));
assert.equal(previous.commercial.faults.result,'PASS');assert.equal(previous.stage,'central archive verification');
const env={...process.env};for(const k of Object.keys(env))if(k.startsWith('COMPOSE_')||k.startsWith('TY_REG_'))delete env[k];
function docker(args,input){return execFileSync('docker',args,{env,input,encoding:'utf8',timeout:30000,maxBuffer:32*1024*1024,stdio:['pipe','pipe','pipe']});}
function inspect(name){return JSON.parse(docker(['inspect',`${meta.project}-${name}-1`]))[0];}
const credentials={},images={};
for(const [name,service]of Object.entries(spec.services)){
 const c=inspect(name);assert.equal(c.Config.Labels['com.docker.compose.project'],meta.project);
 const actual=Object.fromEntries(c.Config.Env.map(e=>[e.slice(0,e.indexOf('=')),e.slice(e.indexOf('=')+1)]));
 for(const [key,value]of Object.entries(service.environment||{})){const match=value.match(/^\$\{([^:]+):/);if(match)credentials[match[1]]=actual[key];}
 images[name]=JSON.parse(docker(['image','inspect',service.image]))[0].Id;
}
Object.assign(env,credentials);
function attest(){guard.validate(meta,JSON.parse(fs.readFileSync(file)));for(const name of Object.keys(spec.services))guard.attest(meta,name,inspect(name),credentials,images);}
function compose(...args){guard.validate(meta,JSON.parse(fs.readFileSync(file)));return docker(['compose','--env-file',process.platform==='win32'?'NUL':'/dev/null','--project-name',meta.project,'--file',file,...args]);}
function sql(query){return docker(['exec','-i',`${meta.project}-mysql-1`,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --batch --skip-column-names --raw'],query+'\n').trim();}
function finance(){return [sql(`SELECT id,compra_id,estado,importe,importe_reintegrado FROM ${meta.schemas.compras}.pagos ORDER BY id;`),sql(`SELECT clave,datos FROM ${meta.schemas.compras}.operaciones_simuladas ORDER BY clave;`),sql(`SELECT ticket_id,batch_id,status FROM ${meta.schemas.entradas}.tickets ORDER BY ticket_id;`)];}
const sleep=ms=>new Promise(r=>setTimeout(r,ms)),requests=[];let token;
async function api(method,path,expected,body){const response=await fetch(urlFor(meta,path),{method,signal:AbortSignal.timeout(25000),headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body?JSON.stringify(body):undefined});requests.push({status:response.status,correlationId:response.headers.get('x-correlation-id')});assert.equal(response.status,expected);return response.json();}
function archive(){return docker(['exec',`${meta.project}-collector-1`,'python3','/collector/archive.py','--all','--limit','100000']).trim().split('\n').filter(Boolean).map(JSON.parse);}
async function delivered(since){
 const logs=Object.fromEntries(guard.APPS.map(name=>[name,docker(['logs','--since',since,`${meta.project}-${name}-1`])]));
 const source=Object.entries(logs).flatMap(([component,text])=>text.split('\n').filter(l=>l.startsWith('{')).map(l=>({...JSON.parse(l),component}))).filter(r=>requests.some(q=>q.correlationId===r.correlationId));
 const audit=source.filter(r=>r.eventType==='AUDIT');let rows;
 for(const deadline=Date.now()+45000;;){rows=archive();if(audit.every(a=>rows.some(r=>r.eventId===a.eventId))&&requests.every(q=>rows.some(r=>r.correlationId===q.correlationId&&r.httpStatus===q.status)))break;assert.ok(Date.now()<deadline,'Fresh log delivery deadline');await sleep(1000);}
 for(const name of guard.APPS){const src=source.find(r=>r.component===name);assert.ok(src,'Fresh logs for '+name);assert.ok(rows.some(r=>r.service?.name===src.service.name&&requests.some(q=>q.correlationId===r.correlationId)));}
 for(const q of requests){const src=source.filter(r=>r.eventType==='HTTP_REQUEST'&&r.correlationId===q.correlationId);const central=rows.filter(r=>r.eventType==='HTTP_REQUEST'&&r.correlationId===q.correlationId);assert.equal(central.length,src.length);assert.equal(new Set(central.map(r=>r.trace.id)).size,1);}
 const text=Object.values(logs).join('\n');for(const secret of [...Object.values(credentials),token].filter(Boolean))assert.ok(!text.includes(secret));
 return {requests:requests.length,services:6,sourceAudit:audit.length,allFreshAuditDelivered:true};
}
async function main(){
 attest();const before=finance(),since=new Date().toISOString();
 const email=`delivery-${crypto.randomUUID()}@example.test`,password=crypto.randomBytes(32).toString('hex');
 await api('POST','/auth/register',201,{email,password});token=(await api('POST','/auth/login',200,{email,password})).access_token;
 compose('stop','--timeout','15','collector');
 await api('GET','/api/v1/venues',200);await api('GET','/disponibilidad/eventos/3/sectores/3',200);
 await api('GET','/compras/3',404);await api('GET','/tickets?orderId=3',404);
 compose('start','collector');
 for(const deadline=Date.now()+60000;inspect('collector').State.Health?.Status!=='healthy';){assert.ok(Date.now()<deadline);await sleep(1000);}
 const outage=await delivered(since);attest();
 // Drain is confirmed before restarting an application; never overlap both outages.
 compose('stop','--timeout','15','compras');compose('start','compras');
 for(const deadline=Date.now()+120000;;){const c=inspect('compras');if(docker(['logs','--since',c.State.StartedAt,c.Id]).includes('Started '))break;assert.ok(Date.now()<deadline);await sleep(1000);}
 await api('GET','/tickets?orderId=3',404);const recovery=await delivered(since);
 attest();assert.deepEqual(finance(),before);
 const result={result:'PASS',project:meta.project,outage,recovery,financialSnapshotUnchanged:true,scope:'Fresh delivery with collector/application outages separated; historical queue loss is not repaired'};
 fs.writeFileSync(require('node:path').join(meta.directory,'fault-logging-check.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
}
main().catch(e=>{console.error('Targeted delivery check FAIL: '+e.name+'; '+String(e.stack).split('\n').find(l=>l.includes('.cjs:'))?.trim());process.exitCode=1;});
