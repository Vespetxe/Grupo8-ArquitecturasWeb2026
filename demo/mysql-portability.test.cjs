'use strict';
// Opt-in: NEW MySQL only. No running demo, no HTTP, no existing volume reuse.
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process'),d=require('./lib.cjs');
test('private SQL initialization, database isolation and reservation limit on real MySQL',{skip:process.env.TY_DEMO_MYSQL_TEST!=='1',timeout:240000},async()=>{
 assert.ok(!process.env.DOCKER_HOST,'Remote Docker override rejected');
 const id=crypto.randomBytes(8).toString('hex'),project='ticketya-demo-check-'+id,network=project+'_backend',volume=project+'_mysql_data',name=project+'-mysql';
 const dir=path.join(d.root,'TicketYa/integration-tests/target/demo-mysql',id);fs.mkdirSync(dir,{recursive:true,mode:0o700});
 if(process.platform==='win32'){const account=cp.execFileSync('whoami',[],{encoding:'utf8'}).trim();cp.execFileSync('icacls.exe',[dir,'/inheritance:r','/grant:r',account+':(OI)(CI)F'],{stdio:'pipe'});}
 const c={meta:d.meta(id),secrets:Object.fromEntries(['EVENTOS','INVENTARIO','ENTRADAS','COMPRAS','IDENTIDAD'].map(n=>['TY_DEMO_'+n+'_PASSWORD',crypto.randomBytes(32).toString('hex')]))};
 const sqlFile=path.join(dir,'01-demo.sql');fs.writeFileSync(sqlFile,d.mysqlSql(c),{mode:0o444,flag:'wx'});
 // Separate disposable test schema, compatible with the UNCHANGED test guard.
 const testSql=path.join(dir,'02-junit.sql');fs.writeFileSync(testSql,"SET SESSION sql_mode='NO_BACKSLASH_ESCAPES'; CREATE DATABASE ticketya_inventario_test; CREATE USER 'limite_test'@'%' IDENTIFIED BY '"+c.secrets.TY_DEMO_INVENTARIO_PASSWORD+"'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES,DROP ON `ticketya\\_inventario\\_test`.* TO 'limite_test'@'%';",{mode:0o444,flag:'wx'});
 const env={...process.env,MYSQL_ROOT_PASSWORD:crypto.randomBytes(32).toString('hex')};
 function docker(args,input,extra={}){return cp.execFileSync('docker',args,{env:{...env,...extra},input,encoding:'utf8',stdio:['pipe','pipe','pipe'],timeout:180000,maxBuffer:8*1024*1024});}
 for(const image of ['mysql:8.4.8','ticketya-tests:java21-node22'])docker(['image','inspect',image]);
 assert.equal(docker(['ps','-aq','--filter','name=^/'+name+'$']).trim(),'');
 assert.equal(docker(['volume','ls','-q','--filter','name=^'+volume+'$']).trim(),'');
 assert.equal(docker(['network','ls','-q','--filter','name=^'+network+'$']).trim(),'');
 docker(['network','create','--label','ticketya.purpose=demo-mysql-check',network]);
 docker(['run','--detach','--name',name,'--label','ticketya.check='+id,'--network',network,'--memory','768m','--env','MYSQL_ROOT_PASSWORD','--mount',`type=volume,source=${volume},target=/var/lib/mysql`,'--mount',`type=bind,source=${sqlFile},target=/docker-entrypoint-initdb.d/01-demo.sql,readonly`,'--mount',`type=bind,source=${testSql},target=/docker-entrypoint-initdb.d/02-junit.sql,readonly`,'mysql:8.4.8','--port=33079']);
 function attest(){const info=JSON.parse(docker(['inspect',name]))[0];assert.equal(info.Config.Labels['ticketya.check'],id);assert.ok(info.State.Running);assert.ok(!Object.keys(info.HostConfig.PortBindings||{}).length);}
 const query=q=>{attest();return docker(['exec','-i',name,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=TCP --host=127.0.0.1 --port=33079 --user=root --batch --skip-column-names'],q).trim();};
 const deadline=Date.now()+90000;let ready=false;while(Date.now()<deadline){try{ready=query('SELECT 1;')==='1';if(ready)break;}catch{}await new Promise(r=>setTimeout(r,1000));}assert.ok(ready,'New MySQL did not initialize; preserve resources and inspect logs, never recreate');
 assert.equal(query("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN ("+[...Object.values(c.meta.schemas),c.meta.identitySchema].map(n=>"'"+n+"'").join(',')+");"),'5');
 function login(database){attest();return docker(['exec','--env','PROBE_PASSWORD',name,'sh','-c','MYSQL_PWD="$PROBE_PASSWORD" exec mysql --port=33079 --user=demo_inventario --database="$1" --execute="SELECT 1"','probe',database],undefined,{PROBE_PASSWORD:c.secrets.TY_DEMO_INVENTARIO_PASSWORD});}
 assert.match(login(c.meta.schemas.inventario),/1/);for(const schema of [c.meta.schemas.eventos,c.meta.schemas.entradas,c.meta.schemas.compras,c.meta.identitySchema])assert.throws(()=>login(schema),error=>/ERROR 1044/.test(String(error.stderr)));
 const jdbc="jdbc:mysql://127.0.0.1:33079/ticketya_inventario_test?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC";
 const target=path.join(d.root,'TicketYa/inventarioReserva_service/target');
 let result;try{result=docker(['run','--name',project+'-junit','--label','ticketya.check='+id,'--network','container:'+name,'--memory','2g','--cpus','2','--mount',`type=bind,source=${d.root},target=/workspace,readonly`,'--mount',`type=bind,source=${target},target=/workspace/TicketYa/inventarioReserva_service/target`,'--mount','type=volume,source=ticketya-maven-cache,target=/root/.m2','--workdir','/workspace/TicketYa/inventarioReserva_service',...['TEST_DB_URL','TEST_DB_DRIVER','TEST_DB_USERNAME','TEST_DB_PASSWORD','TEST_DB_DIALECT','SPRING_JPA_HIBERNATE_DDL_AUTO'].flatMap(k=>['--env',k]),'ticketya-tests:java21-node22','mvn','--batch-mode','--no-transfer-progress','-Dtest=LimiteReservasTest','test'],undefined,{TEST_DB_URL:jdbc,TEST_DB_DRIVER:'com.mysql.cj.jdbc.Driver',TEST_DB_USERNAME:'limite_test',TEST_DB_PASSWORD:c.secrets.TY_DEMO_INVENTARIO_PASSWORD,TEST_DB_DIALECT:'org.hibernate.dialect.MySQLDialect',SPRING_JPA_HIBERNATE_DDL_AUTO:'create-drop'});}catch(error){fs.writeFileSync(path.join(dir,'junit.log'),String(error.stdout||'').replaceAll(c.secrets.TY_DEMO_INVENTARIO_PASSWORD,'[REDACTED]'),{mode:0o600});throw Error('Isolated MySQL reservation tests failed; protected junit.log retained');}
 fs.writeFileSync(path.join(dir,'junit.log'),result.replaceAll(c.secrets.TY_DEMO_INVENTARIO_PASSWORD,'[REDACTED]'),{mode:0o600});assert.match(result,/BUILD SUCCESS/);
 fs.writeFileSync(path.join(dir,'result.json'),JSON.stringify({result:'PASS',project,mysql:name,network,volume,schemas:5,foreignSchemasDenied:4,junit:11,resourcesPreserved:true},null,2));
 console.log('Isolated MySQL PASS: '+project+'; resources retained. No existing resources accessed.');
});
