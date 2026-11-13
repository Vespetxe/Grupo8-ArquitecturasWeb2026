'use strict';
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');
const moduleRoot=path.resolve(__dirname,'..');
const repo=path.resolve(moduleRoot,'../..');
const helperImage='node:22.21.1-alpine';
let stage='preflight';
const env={...process.env};
for(const k of Object.keys(env))if(k.startsWith('COMPOSE_')||k.startsWith('TY_ID_TEST_'))delete env[k];
const id=crypto.randomBytes(8).toString('hex');
const project=`ticketya-id-reg-${id}`;
const schema=`ty_e2_identity_${id}`;
const destination=path.join(moduleRoot,'target','isolated',id);
const rootPassword=crypto.randomBytes(32).toString('hex');
const appPassword=crypto.randomBytes(32).toString('hex');
const adminPassword=crypto.randomBytes(32).toString('hex');
const clientSecret=crypto.randomBytes(32).toString('hex');
Object.assign(env,{TY_ID_TEST_ROOT:rootPassword,TY_ID_TEST_PASSWORD:appPassword});
function docker(args,input,extra={}) {
  return execFileSync('docker',args,{encoding:'utf8',input,env:{...env,...extra},maxBuffer:16*1024*1024,stdio:['pipe','pipe','pipe']});
}
const composeFile=path.join(destination,'compose.json');
function compose(...args){return docker(['compose','--env-file',process.platform==='win32'?'NUL':'/dev/null','--project-name',project,'--file',composeFile,...args]);}
function inspect(name){return JSON.parse(docker(['inspect',name]))[0];}
function snapshot(ids){return ids.map(c=>{const x=inspect(c);return {id:x.Id,status:x.State.Status,start:x.State.StartedAt,finish:x.State.FinishedAt,restarts:x.RestartCount};});}
function mysql(sql,user='root',db){
  return docker(['exec','-i','--env','MYSQL_PWD',`${project}-mysql-1`,'mysql','--user='+user,'--batch','--skip-column-names',...(db?['--database='+db]:[])],sql,
    {MYSQL_PWD:user==='root'?rootPassword:appPassword});
}
function attest(){
  const m=inspect(`${project}-mysql-1`);
  assert.equal(m.Config.Labels['com.docker.compose.project'],project);assert.equal(m.Config.Image,'mysql:8.4.8');
  assert.equal(m.Mounts.length,1);assert.equal(m.Mounts[0].Name,`${project}_mysql_data`);
  assert.deepEqual(Object.keys(m.NetworkSettings.Networks),[`${project}_backend`]);
  assert.equal(Object.keys(m.HostConfig.PortBindings||{}).length,0);assert.equal(m.State.Health.Status,'healthy');
}
async function wait(service,condition){
  const deadline=Date.now()+180000;
  while(Date.now()<deadline){const x=inspect(`${project}-${service}-1`);
    if(condition(x))return;
    assert.notEqual(x.State.Status,'exited','Isolated service exited');
    await new Promise(resolve=>setTimeout(resolve,2000));}
  throw new Error('Readiness deadline exceeded');
}
function helper(restartToken,disabled=false){
  attest();
  const c=inspect(`${project}-identidad-1`);
  assert.equal(c.Config.Labels['com.docker.compose.project'],project);assert.equal(c.Config.User,'10001:10001');
  assert.deepEqual(Object.keys(c.NetworkSettings.Networks),[`${project}_backend`]);
  assert.equal(Object.keys(c.HostConfig.PortBindings||{}).length,0);
  assert.ok(c.Config.Env.includes(`DB_URL=jdbc:mysql://mysql:3306/${schema}?allowPublicKeyRetrieval=true&useSSL=false`));
  let result;
  try { result=docker(['run','--rm','--log-driver','none','--network',`${project}_backend`,
    '--mount',`type=bind,source=${path.join(__dirname,'mysql-smoke.cjs')},target=/smoke.cjs,readonly`,
    '--mount',`type=bind,source=${path.join(destination,'keys','public.pem')},target=/public.pem,readonly`,
    '--env','RUN_ID','--env','VERIFY_RESTART','--env','PREVIOUS_TOKEN','--env','ADMIN_PASSWORD','--env','CLIENT_SECRET','--env','CLIENT_DISABLED',helperImage,'node','/smoke.cjs'],undefined,
    {RUN_ID:id,VERIFY_RESTART:restartToken?'true':'false',PREVIOUS_TOKEN:restartToken||'',ADMIN_PASSWORD:adminPassword,CLIENT_SECRET:clientSecret,CLIENT_DISABLED:disabled?'true':'false'});
  }catch(error){
    const safe=String(error.stderr||'').match(/Identity smoke check: ([a-z ]+)/);
    if(safe)stage=`HTTP: ${safe[1]}`;
    throw new Error('Isolated HTTP check failed');
  }
  return JSON.parse(result);
}
async function main(){
  assert.equal(process.argv.length,2,'No overrides permitted');
  assert.match(schema,/^ty_e2_identity_[a-f0-9]{16}$/);
  const oldIds=docker(['ps','-aq']).trim().split(/\s+/).filter(Boolean);
  const previous=snapshot(oldIds);
  assert.equal(docker(['ps','-aq','--filter',`label=com.docker.compose.project=${project}`]).trim(),'');
  assert.equal(docker(['volume','ls','-q','--filter',`name=^${project}_mysql_data$`]).trim(),'');
  fs.mkdirSync(path.join(destination,'keys'),{recursive:true,mode:0o700});
  if(process.platform==='win32') {
    const account=execFileSync('whoami',[],{encoding:'utf8'}).trim();
    execFileSync('icacls.exe',[destination,'/inheritance:r','/grant:r',`${account}:(OI)(CI)F`],{stdio:'pipe'});
  }
  const keys=crypto.generateKeyPairSync('rsa',{modulusLength:3072,privateKeyEncoding:{type:'pkcs8',format:'pem'},publicKeyEncoding:{type:'spki',format:'pem'}});
  // Test-only artifacts under ignored target; production utility refuses repository paths.
  fs.writeFileSync(path.join(destination,'keys','private.pem'),keys.privateKey,{mode:0o600,flag:'wx'});
  fs.writeFileSync(path.join(destination,'keys','public.pem'),keys.publicKey,{mode:0o644,flag:'wx'});
  // The parent directory remains 0700. Within the read-only container bind, UID
  // 10001 must be able to read these ephemeral test keys on Linux as well.
  if(process.platform!=='win32') {
    fs.chmodSync(path.join(destination,'keys'),0o755);
    fs.chmodSync(path.join(destination,'keys','private.pem'),0o444);
  }
  const spec={name:project,services:{
    mysql:{image:'mysql:8.4.8',environment:{MYSQL_ROOT_PASSWORD:'${TY_ID_TEST_ROOT:?required}'},volumes:['mysql_data:/var/lib/mysql'],networks:['backend'],
      healthcheck:{test:['CMD-SHELL','MYSQL_PWD="$${MYSQL_ROOT_PASSWORD}" mysql --protocol=TCP -h127.0.0.1 -uroot --execute="SELECT 1" >/dev/null 2>&1'],interval:'2s',timeout:'5s',retries:60,start_period:'30s'}},
    identidad:{image:'ticketya-e2-identidad:dev',pull_policy:'never',networks:['backend'],environment:{
      SPRING_PROFILES_ACTIVE:'integracion',DB_URL:`jdbc:mysql://mysql:3306/${schema}?allowPublicKeyRetrieval=true&useSSL=false`,DB_USERNAME:'ty_identidad',DB_PASSWORD:'${TY_ID_TEST_PASSWORD:?required}',
      SPRING_JPA_HIBERNATE_DDL_AUTO:'update',IDENTITY_JWT_ISSUER:'http://identidad:8085',IDENTITY_JWT_AUDIENCES:'ticketya-identidad,ticketya-gateway,ticketya-eventos,ticketya-inventario,ticketya-entradas,ticketya-compras',
      IDENTITY_JWT_PRIVATE_KEY:'/keys/private.pem',IDENTITY_JWT_PUBLIC_KEY:'/keys/public.pem',IDENTITY_JWT_KEY_ID:`test-${id}`},
      volumes:[{type:'bind',source:path.join(destination,'keys'),target:'/keys',read_only:true,bind:{create_host_path:false}}]}
  },volumes:{mysql_data:{}},networks:{backend:{}}};
  fs.writeFileSync(composeFile,JSON.stringify(spec,null,2));
  stage='compose validation';compose('config','--quiet');
  stage='mysql startup';compose('up','--detach','--no-build','mysql');
  await wait('mysql',x=>x.State.Health?.Status==='healthy');attest();
  stage='isolated schema migration';
  function migrate(target){attest();return docker(['exec','-i','--env','TY_E2_IDENTIDAD_DB_PASSWORD','--env','IDENTITY_SCHEMA',`${project}-mysql-1`,'bash','-s','--','--isolated'],
    fs.readFileSync(path.join(repo,'docker/mysql/create-identity.sh'),'utf8').replace(/\r/g,''),
    {TY_E2_IDENTIDAD_DB_PASSWORD:appPassword,IDENTITY_SCHEMA:target});}
  migrate(schema);
  stage='migration repetition and collision guards';
  const grantsBefore=mysql("SHOW GRANTS FOR 'ty_identidad'@'%';");
  assert.ok(!/ALL PRIVILEGES|\bDROP\b|GRANT OPTION|CREATE ROUTINE|TRIGGER/.test(grantsBefore));
  let repeatRejected=false;try{migrate(schema);}catch{repeatRejected=true;}assert.equal(repeatRejected,true);
  assert.equal(mysql("SHOW GRANTS FOR 'ty_identidad'@'%';"),grantsBefore);
  const collisionSchema=`ty_e2_identity_${crypto.randomBytes(8).toString('hex')}`;
  let collisionRejected=false;try{migrate(collisionSchema);}catch{collisionRejected=true;}assert.equal(collisionRejected,true);
  assert.equal(mysql(`SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${collisionSchema}';`).trim(),'0');
  stage='permission fixtures';
  const foreign=['eventos','inventario','entradas','compras'].map(s=>`${schema}_${s}`);
  for(const db of foreign)mysql(`CREATE DATABASE \`${db}\`; CREATE TABLE \`${db}\`.permission_probe (id BIGINT PRIMARY KEY);`);
  assert.equal(mysql('SELECT 1;','ty_identidad',schema).trim(),'1');
  for(const db of foreign){let denied=false;try{mysql(`SELECT COUNT(*) FROM \`${db}\`.permission_probe;`,'ty_identidad',schema);}catch{denied=true;}assert.equal(denied,true);}
  // Same-length underscore substitute would match an incorrectly unescaped GRANT.
  const wildcardTwin=schema.replaceAll('_','x');
  mysql(`CREATE DATABASE \`${wildcardTwin}\`; CREATE TABLE \`${wildcardTwin}\`.permission_probe (id BIGINT PRIMARY KEY);`);
  let wildcardDenied=false;try{mysql(`SELECT COUNT(*) FROM \`${wildcardTwin}\`.permission_probe;`,'ty_identidad',schema);}catch{wildcardDenied=true;}
  assert.equal(wildcardDenied,true);
  stage='identity startup';compose('up','--detach','--no-build','--no-deps','identidad');
  await wait('identidad',x=>{const logs=docker(['logs',x.Id]);return logs.includes('Started IdentidadApplication');});
  stage='controlled CLI provisioning';
  function provision(data){
    attest();
    const file=path.join(destination,'provision.properties');
    fs.writeFileSync(file,Object.entries(data).map(([k,v])=>`${k}=${v}`).join('\n'),{mode:0o600});
    compose('run','--rm','--no-deps','--volume',`${file}:/run/provision.properties:ro`,
      '--env','IDENTITY_PROVISION_FILE=/run/provision.properties','identidad','provision');
  }
  provision({operation:'first-admin',email:`admin-${id}@example.test`,password:adminPassword});
  let repeated=false;try{provision({operation:'first-admin',email:`duplicate-${id}@example.test`,password:adminPassword});}catch{repeated=true;}
  assert.equal(repeated,true);
  provision({operation:'user',email:`organizer-${id}@example.test`,password:crypto.randomBytes(32).toString('hex'),role:'ORGANIZADOR'});
  provision({operation:'user',email:`staff-${id}@example.test`,password:crypto.randomBytes(32).toString('hex'),role:'PERSONAL_ACCESO'});
  provision({operation:'client',caller:'entradas',audience:'ticketya-compras',scopes:'orders.read',secret:clientSecret});
  stage='HTTP authentication and signature';docker(['pull',helperImage]);const result=helper();
  stage='persisted account';assert.equal(mysql(`SELECT COUNT(*) FROM identity_users WHERE password_hash LIKE '{argon2id-v1}$argon2id$%';`,'ty_identidad',schema).trim(),'4');
  stage='isolated restart';compose('restart','--timeout','60','identidad');
  // Verify new startup completion, not the previous boot line retained in logs.
  await wait('identidad',x=>docker(['logs','--since',x.State.StartedAt,x.Id]).includes('Started IdentidadApplication'));
  const restarted=helper(result.token);assert.equal(restarted.restartVerified,true);
  assert.equal(restarted.technicalClientReloaded,true);
  stage='technical client disable';attest();
  mysql("UPDATE identity_technical_clients SET enabled=false WHERE client_id='entradas-to-compras';",'ty_identidad',schema);
  assert.equal(helper(result.token,true).disabledClientRejected,true);
  compose('restart','--timeout','60','identidad');
  await wait('identidad',x=>docker(['logs','--since',x.State.StartedAt,x.Id]).includes('Started IdentidadApplication'));
  assert.equal(helper(result.token,true).disabledClientRejected,true);
  stage='existing resources';assert.deepEqual(snapshot(oldIds),previous);
  const report={project,schema,services:['mysql','identidad'],tables:mysql('SHOW TABLES;','ty_identidad',schema).trim().split('\n'),
    httpChecks:result.checks,restartVerified:true,foreignSchemasDenied:4,previousContainersUnchanged:true,portsPublished:0,cliProvisioningVerified:true,
    migrationRepeatRejected:true,migrationUserCollisionRejectedBeforeDDL:true,limitedPrivilegesVerified:true,wildcardSchemaDenied:true,
    technicalClientReloaded:true,disabledClientRejectedBeforeAndAfterRestart:true};
  fs.writeFileSync(path.join(destination,'report.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify(report,null,2));
}
main().catch(()=>{console.error(`Identity verification failed at: ${stage}. No automatic cleanup or retry. Project: ${project}. Inspect without exposing secrets.`);process.exitCode=1;});
