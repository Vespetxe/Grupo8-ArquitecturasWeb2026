'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),cp=require('node:child_process'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../../..');
function contract(args,input){assert.ok(args.includes('--interactive'));assert.ok(args.includes('-T'));assert.ok(args.includes('IDENTITY_PROVISION_FILE=-'));assert.ok(!args.includes('--volume'));assert.ok(!args.includes('--user'));assert.ok(!args.join(' ').includes('secret-sentinel'));assert.equal(input,'operation=user\npassword=secret-sentinel');}
test('demo provisions through stdin without secret files, permission expansion or root override',async()=>{
 const source=fs.readFileSync(path.join(root,'demo/start.cjs'),'utf8'),start=source.indexOf(' async function provision(key,values)'),end=source.indexOf('\n for(const u',start),calls=[];
 const d={attest(){},composeInput(c,input,...args){calls.push({args,input});}};
 const fn=vm.runInNewContext(source.slice(start,end)+';provision',{d,c:{},journal:{step:async(k,action)=>action()},fs:{writeFileSync(){throw Error('Secret file forbidden');}}});
 await fn('test',{operation:'user',password:'secret-sentinel'});assert.equal(calls.length,1);contract(calls[0].args,calls[0].input);
});
test('E2 provisions through stdin without secret files, bind mounts or root override',()=>{
 const source=fs.readFileSync(path.join(__dirname,'run-security.cjs'),'utf8'),start=source.indexOf(' function provision(values)'),end=source.indexOf('\n const adminPassword',start),calls=[];
 const fn=vm.runInNewContext(source.slice(start,end)+';provision',{attest(){},provisionLogs:[],composeInput(input,...args){calls.push({args,input});return 'completed';},fs:{writeFileSync(){throw Error('Secret file forbidden');}}});
 fn({operation:'user',password:'secret-sentinel'});assert.equal(calls.length,1);contract(calls[0].args,calls[0].input);
});
test('Identity stays non-root and uses the Java file reader compatible with direct stdin',()=>{
 assert.match(fs.readFileSync(path.join(root,'TicketYa/identidad_service/Dockerfile'),'utf8'),/USER 10001:10001/);
 assert.match(fs.readFileSync(path.join(root,'TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/config/ProvisioningCommand.java'),'utf8'),/Files\.newBufferedReader\(Path\.of\(location\)\)/);
});
test('Linux real UID 10001 cannot read root-owned 0600 but can load properties from stdin with Java',{skip:process.env.TY_PROVISION_DOCKER_TEST!=='1'},()=>{
 const dir=path.join(root,'TicketYa/integration-tests/target/provision-permissions',crypto.randomBytes(8).toString('hex'));fs.mkdirSync(dir,{recursive:true});
 const file=path.join(dir,'ReadProvision.java');fs.writeFileSync(file,'import java.nio.file.*;import java.util.*;class ReadProvision{public static void main(String[] a)throws Exception{if(Files.isReadable(Path.of("/tmp/root-only.properties")))throw new AssertionError("0600 fixture unexpectedly readable");Properties p=new Properties();try(var r=new java.io.InputStreamReader(System.in,java.nio.charset.StandardCharsets.UTF_8)){p.load(r);}if(!"probe".equals(p.getProperty("operation"))||!"not-a-real-secret".equals(p.getProperty("password")))throw new AssertionError("stdin properties unreadable");p.clear();System.out.println("PASS: UID 10001 reads stdin; root-owned 0600 remains confidential");}}',{mode:0o644});
 const args=['run','--name','ticketya-provision-permissions-'+crypto.randomBytes(8).toString('hex'),'--label','ticketya.purpose=provision-permissions','--entrypoint','sh','--network','none','--read-only','--memory','512m','--cpus','1','--cap-drop','ALL','--cap-add','SETUID','--cap-add','SETGID','--security-opt','no-new-privileges:true','--tmpfs','/tmp:rw,nosuid,nodev,size=128m,mode=1777','--mount',`type=bind,source=${file},target=/probe/ReadProvision.java,readonly`,'--interactive','ticketya-tests:java21-node22','-c','printf fixture > /tmp/root-only.properties && chmod 600 /tmp/root-only.properties && exec setpriv --reuid=10001 --regid=10001 --clear-groups sh -c \'test "$(id -u)" = 10001 && exec java --source 21 /probe/ReadProvision.java\''];
 const output=cp.execFileSync('docker',args,{input:'operation=probe\npassword=not-a-real-secret\n',encoding:'utf8',timeout:60000,stdio:['pipe','pipe','pipe']});assert.match(output,/PASS: UID 10001 reads stdin/);assert.ok(!output.includes('not-a-real-secret'));
});
