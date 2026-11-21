'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process');
const d=require('./lib.cjs');
async function prepare(){
 d.validateCatalog();
 if(fs.existsSync(d.runtime)){d.readConfig();console.log('Demo already prepared; keys and credentials were NOT replaced.');return;}
 fs.mkdirSync(d.runtime,{mode:0o700});
 if(process.platform==='win32'){const account=cp.execFileSync('whoami',[],{encoding:'utf8'}).trim();cp.execFileSync('icacls.exe',[d.runtime,'/inheritance:r','/grant:r',account+':(OI)(CI)F'],{stdio:'pipe'});}
 fs.mkdirSync(path.join(d.runtime,'keys'),{mode:0o700});
 const config={meta:d.meta(crypto.randomBytes(8).toString('hex')),catalogHash:d.hash(),secrets:{},users:d.catalog.users.map(u=>({...u,password:crypto.randomBytes(32).toString('hex')}))};
 for(const n of d.passwordNames)config.secrets['TY_DEMO_'+n+'_PASSWORD']=crypto.randomBytes(32).toString('hex');
 for(const p of Object.keys(d.calls))config.secrets[d.secretName(p)]=crypto.randomBytes(32).toString('hex');
 const keys=crypto.generateKeyPairSync('rsa',{modulusLength:3072,privateKeyEncoding:{type:'pkcs8',format:'pem'},publicKeyEncoding:{type:'spki',format:'pem'}});
 for(const [name,value]of [['private.pem',keys.privateKey],['public.pem',keys.publicKey]])fs.writeFileSync(path.join(d.runtime,'keys',name),value,{mode:0o600,flag:'wx'});
 // Parent runtime remains 0700; only the read-only bind is traversable by the
 // container's non-root UID 10001. Existing credentials/keys are never changed.
 if(process.platform!=='win32'){fs.chmodSync(path.join(d.runtime,'keys'),0o755);fs.chmodSync(path.join(d.runtime,'keys/private.pem'),0o444);fs.chmodSync(path.join(d.runtime,'keys/public.pem'),0o444);}
 fs.writeFileSync(path.join(d.runtime,'private.json'),JSON.stringify(config,null,2),{mode:0o600,flag:'wx'});
 fs.writeFileSync(path.join(d.runtime,'docker-compose.json'),JSON.stringify(d.spec(config.meta),null,2),{mode:0o600,flag:'wx'});
 fs.writeFileSync(path.join(d.runtime,'ACCESOS-DEMO.md'),'# Accesos ficticios exclusivos de esta demo\n\nNo compartir ni versionar este archivo.\n\n'+config.users.map(u=>`- ${u.role} (${u.key}): ${u.email}\n  Contraseña: ${u.password}`).join('\n')+'\n',{mode:0o600,flag:'wx'});
 d.readConfig();console.log('Prepared '+config.meta.project+'. No Docker or database operations performed. Credentials are in demo/runtime/ACCESOS-DEMO.md (private, ignored).');
}
if(require.main===module)d.main(prepare);
