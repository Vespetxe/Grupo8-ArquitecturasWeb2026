'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const path=require('node:path');
const os=require('node:os');
const fs=require('node:fs');
const crypto=require('node:crypto');
const {generate}=require('./prepare-keys.cjs');
test('key generator refuses relative paths',()=>assert.throws(()=>generate('local-keys')));
test('key generator refuses repository destinations',()=>assert.throws(()=>generate(path.resolve(__dirname,'../../../identity-keys'))));
test('key generator refuses an existing external directory',()=>assert.throws(()=>generate(os.tmpdir())));
test('new temporary keys are protected and cannot be overwritten',()=>{
  const temporary=fs.mkdtempSync(path.join(os.tmpdir(),'ticketya-key-audit-'));
  const destination=path.join(temporary,'keys');
  try {
    generate(destination);
    const file=path.join(destination,'private.pem');
    const digest=crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
    const key=crypto.createPrivateKey(fs.readFileSync(file));
    assert.equal(key.asymmetricKeyDetails.modulusLength,3072);
    assert.throws(()=>generate(destination));
    assert.equal(crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex'),digest);
    if(process.platform!=='win32')assert.equal(fs.statSync(file).mode&0o777,0o600);
    else {
      const {execFileSync}=require('node:child_process');
      const acl=execFileSync('icacls.exe',[file],{encoding:'utf8'});
      assert.ok(!acl.includes('(I)'),'Private key must not inherit access entries');
    }
  } finally {
    // Remove only this newly created test fixture, never a supplied/existing key directory.
    assert.equal(path.dirname(fs.realpathSync(temporary)),fs.realpathSync(os.tmpdir()));
    assert.ok(path.basename(temporary).startsWith('ticketya-key-audit-'));
    fs.rmSync(temporary,{recursive:true});
  }
});
