'use strict';
const fs=require('node:fs');
const path=require('node:path');
const {generateKeyPairSync}=require('node:crypto');

// Generation is an explicit operator action, never part of application startup.
function generate(destination) {
  if(!destination || !path.isAbsolute(destination))throw new Error('Specify an absolute external directory');
  const root=fs.realpathSync(path.resolve(__dirname,'../../..'));
  const parent=fs.realpathSync(path.dirname(destination));
  const dir=path.join(parent,path.basename(destination));
  const relative=path.relative(root,dir);
  if(!relative || (!relative.startsWith('..'+path.sep) && relative!=='..' && !path.isAbsolute(relative)))
    throw new Error('Keys must be outside the repository');
  if(fs.existsSync(dir))throw new Error('Destination exists; refusing to replace keys');
  fs.mkdirSync(dir,{mode:0o700});
  let account;
  if(process.platform==='win32') {
    const {execFileSync}=require('node:child_process');
    account=execFileSync('whoami',[],{encoding:'utf8'}).trim();
    // Restrict the directory before any private material is written.
    execFileSync('icacls.exe',[dir,'/inheritance:r','/grant:r',`${account}:(OI)(CI)F`],{stdio:'pipe'});
  }
  const keys=generateKeyPairSync('rsa',{modulusLength:3072,
    privateKeyEncoding:{type:'pkcs8',format:'pem'},publicKeyEncoding:{type:'spki',format:'pem'}});
  fs.writeFileSync(path.join(dir,'private.pem'),keys.privateKey,{flag:'wx',mode:0o600});
  fs.writeFileSync(path.join(dir,'public.pem'),keys.publicKey,{flag:'wx',mode:0o644});
  if(process.platform==='win32') {
    // chmod alone cannot restrict Windows ACLs. Fail if the dedicated ACL cannot be set.
    const {execFileSync}=require('node:child_process');
    for(const file of ['private.pem','public.pem'])execFileSync('icacls.exe',[path.join(dir,file),'/inheritance:r','/grant:r',`${account}:F`],{stdio:'pipe'});
  }
  return dir;
}
if(require.main===module) {
  try {generate(process.argv[2]);console.log('External identity keys created; no key material printed');}
  catch {console.error('Key preparation failed. Inspect the destination and permissions before any retry.');process.exitCode=1;}
}
module.exports={generate};
