'use strict';
// The default Maven test phase is offline business-wise: no -Pe2e, no live DB URLs.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto');
const environment=require('./e2/environment.cjs');
const root=path.resolve(__dirname,'../..'),image='ticketya-tests:java21-node22';
if(process.argv.length!==2)throw Error('No arguments supported; this command runs integration-module tests, not live E2E');
const env={...process.env};for(const k of Object.keys(env))if(k.startsWith('COMPOSE_'))delete env[k];
function run(args,options={}){return cp.spawnSync('docker',args,{cwd:root,env,stdio:'inherit',timeout:900000,...options});}
let imageId;try{environment.nodeVersion();const inspect=args=>cp.execFileSync('docker',args,{env,encoding:'utf8',stdio:['pipe','pipe','pipe'],timeout:15000});environment.engine(inspect);imageId=environment.inspectImage(inspect,image);}catch(error){console.error((error.code||'ENVIRONMENT')+': '+error.message);if(error.hint)console.error(error.hint);process.exit(1);}
if(!imageId){console.log('Building reproducible Java 21 / Maven 3.9.11 / Node 22.21.1 test image');const build=run(['build','--progress=plain','--tag',image,'--file',path.join(root,'Dockerfile.tests'),path.join(root,'docker')]);if(build.status!==0){console.error('Test image build failed; no test containers started. Check Docker/registry connectivity.');process.exit(1);}}
const id='portabilidad-'+crypto.randomBytes(6).toString('hex'),target=path.join(__dirname,'target');fs.mkdirSync(target,{recursive:true});
console.log('Running tests; report: TicketYa/integration-tests/target/runs/'+id+'/surefire-reports');
const r=run(['run','--name','ticketya-tests-'+id,'--label','ticketya.purpose=offline-tests','--memory','2g','--cpus','2','--mount',`type=bind,source=${root},target=/workspace,readonly`,'--mount',`type=bind,source=${target},target=/workspace/TicketYa/integration-tests/target`,'--mount','type=volume,source=ticketya-maven-cache,target=/root/.m2','--workdir','/workspace/TicketYa/integration-tests',image,'mvn','--batch-mode','--no-transfer-progress','-Dticketya.run='+id,'test']);
process.exitCode=r.status===0?0:1;if(r.status!==0)console.error('Tests failed or Docker unavailable. Preserve the named container and reports; no automatic retry or cleanup.');
