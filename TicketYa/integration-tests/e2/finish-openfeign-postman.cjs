'use strict';
// Only the standalone collection remains; load/browser/financial regression are not repeated.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),assert=require('node:assert/strict');
let secrets=[],result,directory;
async function main(){
 assert.equal(process.argv[2],'--run');assert.match(process.argv[3]||'',/^[a-f0-9]{16}$/);assert.equal(process.argv.length,4);
 directory=path.join(__dirname,'../target/security',process.argv[3]);
 const context=JSON.parse(fs.readFileSync(path.join(directory,'newman-private-context.json'))),{meta,adminPassword,provisioned}=context;
 assert.equal(meta.id,process.argv[3]);assert.equal(meta.project,'ticketya-e2-reg-'+meta.id);
 const previous=JSON.parse(fs.readFileSync(path.join(directory,'continuation-report.json')));assert.equal(previous.stage,'standalone Postman Collection Runner flow');assert.equal(previous.load.result,'PASS');assert.equal(previous.frontend.result,'PASS');
 const failed=JSON.parse(fs.readFileSync(path.join(directory,'newman-flows-results.json')));assert.equal(failed.requests,1);assert.equal(failed.results[0].status,201);assert(failed.failures.every(f=>f.message.includes("Identifier 'data'")));
 const oldFile=path.join(directory,'newman-flows-initial-script-failure.json');if(!fs.existsSync(oldFile))fs.copyFileSync(path.join(directory,'newman-flows-results.json'),oldFile);
 const spec=JSON.parse(fs.readFileSync(path.join(directory,'compose.json')));process.env.TY_FEIGN_IMAGE_TAG=spec.services.eventos.image.split(':')[1];require('./recommendation-isolation.cjs').validate(meta,spec);
 function docker(args,input,extra={},timeout=30000){return cp.execFileSync('docker',args,{input,env:{...process.env,...extra},encoding:'utf8',maxBuffer:32*1024*1024,timeout,stdio:['pipe','pipe','pipe']});}
 const inspect=name=>JSON.parse(docker(['inspect',meta.project+'-'+name+'-1']))[0];
 const containers=Object.fromEntries(Object.keys(spec.services).map(name=>[name,inspect(name)]));
 for(const[name,c]of Object.entries(containers)){assert.equal(c.Config.Labels['com.docker.compose.project'],meta.project);assert.equal(c.State.Running,true);assert.equal(c.RestartCount,0);assert.equal(c.Config.Image,spec.services[name].image);}
 secrets=[adminPassword,...provisioned.map(p=>p.password),...Object.values(containers).flatMap(c=>c.Config.Env).filter(v=>/PASSWORD=|SECRET=/.test(v)).map(v=>v.slice(v.indexOf('=')+1))];
 function sql(query){assert(/^SELECT\s/i.test(query));assert(!query.replace(/;\s*$/,'').includes(';'));assert.equal(inspect('mysql').State.Health.Status,'healthy');return docker(['exec','-i',meta.project+'-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --batch --skip-column-names --raw'],query+'\n').trim();}
 result={project:meta.project,result:'RUNNING',originalCampaign:'report.json (FAIL preserved)',continuation:'continuation-report.json (preserved)',business:JSON.parse(fs.readFileSync(path.join(directory,'report.json'))).commercial,controllerProbes:previous.controllerProbes,tracing:previous.tracing,audit:previous.audit,load:previous.load,frontend:previous.frontend};
 const save=()=>fs.writeFileSync(path.join(directory,'openfeign-final-results.json'),JSON.stringify(result,null,2));save();
 console.log('Executing only corrected standalone Postman flow');
 result.newmanFlows=require('./newman-flows-run.cjs')({meta,docker,adminPassword,provisioned,sql,suffix:'-fixed'});
 result.newmanContinuation=JSON.parse(fs.readFileSync(path.join(directory,'continuation-newman-resumed/newman-progress.json')));
 for(const[table,key,schema]of [['pagos','compra_id','compras'],['ticket_batches','order_id','entradas']])assert.equal(sql(`SELECT COUNT(*) FROM (SELECT ${key} FROM ${meta.schemas[schema]}.${table} GROUP BY ${key} HAVING COUNT(*)>1) duplicates;`),'0');
 result.financialTotals=sql(`SELECT (SELECT COUNT(*) FROM ${meta.schemas.compras}.pagos),(SELECT COUNT(*) FROM ${meta.schemas.compras}.reintegros),(SELECT COUNT(*) FROM ${meta.schemas.entradas}.ticket_batches),(SELECT COUNT(*) FROM ${meta.schemas.entradas}.tickets);`);
 result.noDuplicatePaymentsOrBatches=true;
 const cutoff=JSON.parse(docker(['inspect','ticketya-feign-compile-entradas-20261126']))[0].Created;
 const allIds=docker(['ps','-aq']).trim().split(/\s+/);const historical=JSON.parse(docker(['inspect',...allIds])).filter(c=>c.Created<cutoff);
 for(const c of historical){assert(c.State.StartedAt<cutoff,'Historical container started during this work');assert(c.State.FinishedAt<cutoff,'Historical container stopped during this work');}
 result.historicalContainers={count:historical.length,unchangedSince:cutoff};
 try{result.persistentVolume=JSON.parse(docker(['volume','inspect','ticketya-e2-infra_mysql_data']))[0].Name;}catch{result.persistentVolume='Named historical volume unavailable; no volume operations executed';}
 for(const name of ['gateway','identidad','eventos','inventario','compras','entradas']){const text=docker(['logs',containers[name].Id]);for(const secret of secrets.filter(Boolean))assert(!text.includes(secret),'Secret in '+name+' logs');assert(!/eyJ[\w-]+\.[\w-]+\.[\w-]+/.test(text),'JWT in '+name+' logs');}
 result.result='PASS';save();console.log(JSON.stringify({result:'PASS',standaloneRequests:result.newmanFlows.requests,standaloneAssertions:result.newmanFlows.assertions,financialTotals:result.financialTotals,historicalContainers:historical.length}));
}
main().catch(error=>{let message=String(error.stderr||error.message);for(const secret of secrets.filter(Boolean))message=message.replaceAll(secret,'[REDACTED]');message=message.replace(/eyJ[\w-]+\.[\w-]+\.[\w-]+/g,'[TOKEN]').slice(-2000);if(result&&directory){result.result='FAIL';result.diagnostic=message;fs.writeFileSync(path.join(directory,'openfeign-final-results.json'),JSON.stringify(result,null,2));}console.error('Stopped: '+message);process.exitCode=1;});
