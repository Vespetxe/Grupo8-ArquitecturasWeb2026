'use strict';
const assert=require('node:assert/strict');
const crypto=require('node:crypto');
const fs=require('node:fs');
const base='http://identidad:8085';
const password=crypto.randomBytes(32).toString('hex');
let check='registration';
async function serviceToken(expected){
  const response=await fetch(base+'/oauth2/token',{method:'POST',redirect:'error',signal:AbortSignal.timeout(10000),
    headers:{Authorization:'Basic '+Buffer.from('entradas-to-compras:'+process.env.CLIENT_SECRET).toString('base64'),
      'Content-Type':'application/x-www-form-urlencoded'},body:'grant_type=client_credentials&scope=orders.read'});
  assert.equal(response.status,expected);
  return expected===200?response.json():null;
}
async function request(method,path,data,token) {
  assert.ok(path.startsWith('/auth/') || path==='/oauth2/jwks');
  const response=await fetch(base+path,{method,redirect:'error',signal:AbortSignal.timeout(10000),
    headers:{...(data?{'Content-Type':'application/json'}:{}),...(token?{Authorization:`Bearer ${token}`}:{})},
    body:data?JSON.stringify(data):undefined});
  return {status:response.status,data:await response.json().catch(()=>({}))};
}
async function main(){
  const email=`mysql-${process.env.RUN_ID}@example.test`;
  if(process.env.VERIFY_RESTART==='true') {
    check='persisted user and technical client';
    const response=await request('GET','/auth/me',null,process.env.PREVIOUS_TOKEN);
    assert.equal(response.status,200);assert.equal(response.data.email,email);
    await serviceToken(process.env.CLIENT_DISABLED==='true'?401:200);
    console.log(JSON.stringify({restartVerified:true,technicalClientReloaded:true,disabledClientRejected:process.env.CLIENT_DISABLED==='true'}));return;
  }
  const register=await request('POST','/auth/register',{email,password});assert.equal(register.status,201);
  assert.deepEqual(register.data.roles,['CLIENTE']);assert.ok(register.data.id>0);
  assert.equal(register.data.password,undefined);assert.equal(register.data.passwordHash,undefined);
  assert.equal((await request('POST','/auth/register',{email,password})).status,409);
  assert.equal((await request('POST','/auth/register',{email:`inject-${email}`,password,roles:['ADMINISTRADOR']})).status,400);
  assert.equal((await request('POST','/auth/login',{email,password:crypto.randomBytes(24).toString('hex')})).status,401);
  check='login';const login=await request('POST','/auth/login',{email,password});assert.equal(login.status,200);
  const token=login.data.access_token;assert.ok(typeof token==='string');
  const [header,payload,signature]=token.split('.');
  const h=JSON.parse(Buffer.from(header,'base64url'));const c=JSON.parse(Buffer.from(payload,'base64url'));
  check='human signature and claims';assert.equal(h.alg,'RS256');assert.equal(h.kid,`test-${process.env.RUN_ID}`);
  assert.ok(crypto.verify('RSA-SHA256',Buffer.from(`${header}.${payload}`),fs.readFileSync('/public.pem'),Buffer.from(signature,'base64url')));
  assert.equal(c.iss,'http://identidad:8085');assert.equal(c.token_use,'user');assert.equal(c.sub,String(register.data.id));
  assert.ok(c.aud.includes('ticketya-identidad'));assert.deepEqual(c.roles,['CLIENTE']);
  assert.equal(c.exp-c.iat,900);assert.equal(c.nbf,c.iat);assert.ok(c.jti);
  assert.equal((await request('GET','/auth/me',null,token)).status,200);
  assert.equal((await request('GET','/auth/me')).status,401);
  const tampered=Buffer.from(signature,'base64url');tampered[0]^=1;
  assert.equal((await request('GET','/auth/me',null,`${header}.${payload}.${tampered.toString('base64url')}`)).status,401);
  check='public jwks';const jwks=await request('GET','/oauth2/jwks');assert.equal(jwks.status,200);
  assert.equal(jwks.data.keys[0].kid,h.kid);assert.equal(jwks.data.keys[0].d,undefined);
  check='administrator login';const admin=await request('POST','/auth/login',{email:`admin-${process.env.RUN_ID}@example.test`,password:process.env.ADMIN_PASSWORD});
  assert.equal(admin.status,200);
  const adminClaims=JSON.parse(Buffer.from(admin.data.access_token.split('.')[1],'base64url'));
  assert.deepEqual(adminClaims.roles,['ADMINISTRADOR']);assert.equal(adminClaims.exp-adminClaims.iat,300);
  check='service token';const serviceBody=await serviceToken(200);
  const [sh,sp,ss]=serviceBody.access_token.split('.');const serviceClaims=JSON.parse(Buffer.from(sp,'base64url'));
  assert.ok(crypto.verify('RSA-SHA256',Buffer.from(`${sh}.${sp}`),fs.readFileSync('/public.pem'),Buffer.from(ss,'base64url')));
  assert.equal(serviceClaims.token_use,'service');assert.equal(serviceClaims.roles,undefined);
  assert.deepEqual(Array.isArray(serviceClaims.aud)?serviceClaims.aud:[serviceClaims.aud],['ticketya-compras']);assert.equal(serviceClaims.exp-serviceClaims.iat,120);
  assert.equal((await request('GET','/auth/me',null,serviceBody.access_token)).status,401);
  // IPC to parent only: the runner captures this output and never prints/persists the token.
  console.log(JSON.stringify({userId:register.data.id,token,checks:18}));
}
main().catch(()=>{console.error(`Identity smoke check: ${check}`);process.exitCode=1;});
