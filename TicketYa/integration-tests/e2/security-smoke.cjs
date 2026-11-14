'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),crypto=require('node:crypto');
const {CALLS,SCOPES}=require('/security-isolation.cjs');
const id=process.env.RUN_ID,issuer='http://identidad:8085';
const ports={eventos:8081,inventario:8082,entradas:8083,compras:8084,gateway:8080};
let checks=0;const requests=[];
async function request(target,method,p,token,body,expected,extra={}){
 assert.ok(Object.hasOwn(ports,target)||target==='identidad');
 const url=`http://${target}:${target==='identidad'?8085:ports[target]}${p}`;
 const r=await fetch(url,{method,redirect:'error',signal:AbortSignal.timeout(20000),headers:{...extra,...(token?{Authorization:'Bearer '+token}:{}),...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});
 requests.push({target,method,path:p,status:r.status,expected});
 assert.equal(r.status,expected,`${target} ${method} ${p}: expected ${expected}, received ${r.status}`);checks++;return r.json().catch(()=>null);
}
function sign(audience,overrides={}){
 const now=Math.floor(Date.now()/1000),header=Buffer.from(JSON.stringify({alg:'RS256',kid:`security-${id}`})).toString('base64url');
 const payload=Buffer.from(JSON.stringify({iss:issuer,sub:'1',aud:['ticketya-'+audience],iat:now-1,nbf:now-1,exp:now+300,jti:crypto.randomUUID(),token_use:'user',roles:['CLIENTE'],...overrides})).toString('base64url');
 return `${header}.${payload}.${crypto.sign('RSA-SHA256',Buffer.from(`${header}.${payload}`),fs.readFileSync('/keys/private.pem')).toString('base64url')}`;
}
async function technical(caller,target){
 const secret=process.env[`TY_REG_${caller.toUpperCase()}_${target.toUpperCase()}_SECRET`];
 const r=await fetch(issuer+'/oauth2/token',{method:'POST',redirect:'error',signal:AbortSignal.timeout(20000),headers:{Authorization:'Basic '+Buffer.from(`${caller}-to-${target}:${secret}`).toString('base64'),'Content-Type':'application/x-www-form-urlencoded'},body:'grant_type=client_credentials&scope='+encodeURIComponent(SCOPES[caller+':'+target])});
 requests.push({target:'identidad',method:'POST',path:'/oauth2/token',caller,destination:target,status:r.status,expected:200});
 assert.equal(r.status,200);checks++;return (await r.json()).access_token;
}
async function main(){
 if(process.env.SIGNATURE_ONLY==='true'){
  const valid=sign('gateway',{aud:['ticketya-gateway','ticketya-eventos','ticketya-inventario','ticketya-entradas','ticketya-compras','ticketya-identidad']});
  const parts=valid.split('.'),signature=Buffer.from(parts[2],'base64url');signature[0]^=1;
  const tampered=`${parts[0]}.${parts[1]}.${signature.toString('base64url')}`;
  const unsigned=Buffer.from(JSON.stringify({alg:'none'})).toString('base64url')+'.'+parts[1]+'.';
  for(const [target,p]of Object.entries({gateway:'/auth/me',eventos:'/api/v1/events/999',inventario:'/reservas/999',entradas:'/tickets?orderId=999',compras:'/internal/orders/999'}))
   for(const token of [tampered,unsigned])await request(target,'GET',p,token,null,401);
  console.log(JSON.stringify({checks,requests,signatureOnly:true}));return;
 }
 const email=`client-${id}@example.test`,password=crypto.randomBytes(32).toString('hex');
 await request('gateway','POST','/auth/register',null,{email,password},201);
 await request('gateway','POST','/auth/login',null,{email,password:crypto.randomBytes(32).toString('hex')},401);
 const human=(await request('gateway','POST','/auth/login',null,{email,password},200)).access_token;
 const parts=human.split('.'),badSignature=Buffer.from(parts[2],'base64url');badSignature[0]^=1;
 const tampered=`${parts[0]}.${parts[1]}.${badSignature.toString('base64url')}`;
 const unsigned=Buffer.from(JSON.stringify({alg:'none'})).toString('base64url')+'.'+parts[1]+'.';
 await request('gateway','GET','/auth/me',human,null,200);
 for(const token of ['invalid',tampered,unsigned,sign('gateway',{iss:'http://wrong.invalid'}),sign('wrong'),
  sign('gateway',{exp:Math.floor(Date.now()/1000)-60,iat:Math.floor(Date.now()/1000)-180,nbf:Math.floor(Date.now()/1000)-180}),
  sign('gateway',{nbf:Math.floor(Date.now()/1000)+120}),
  sign('gateway',{token_use:'service',sub:'service:compras-to-gateway',roles:undefined})])await request('gateway','GET','/auth/me',token,null,401);
 await request('gateway','GET','/api/v1/venues',null,null,200);
 await request('gateway','GET','/api/v1/events/search',null,null,200);
 await request('gateway','GET','/compras/1',null,null,401);
 await request('gateway','GET','/compras/1',human,null,404);
 await request('gateway','GET','/admin/devoluciones?estado=PENDIENTE',human,null,403,{'X-Admin-Id':'41001','X-User-Role':'ADMIN'});
 const admin=(await request('gateway','POST','/auth/login',null,{email:`admin-${id}@example.test`,password:process.env.ADMIN_PASSWORD},200)).access_token;
 await request('gateway','GET','/admin/devoluciones?estado=PENDIENTE',admin,null,200);
 await request('gateway','POST','/admin/eventos/999/cancelacion',admin,{},403);
 await request('gateway','POST','/oauth2/token',null,{},401);
 const endpoints={eventos:['/api/v1/events/999','compras','events.read',404],inventario:['/reservas/999','compras','reservations.read',404],entradas:['/tickets?orderId=999','compras','tickets.read',200],compras:['/internal/orders/999','entradas','orders.read',404]};
 for(const [target,[p,caller,scope,ok]]of Object.entries(endpoints)){
  for(const token of [null,'invalid',tampered,unsigned,sign(target,{exp:Math.floor(Date.now()/1000)-60,iat:Math.floor(Date.now()/1000)-180,nbf:Math.floor(Date.now()/1000)-180}),sign(target,{iss:'http://wrong.invalid'}),sign('wrong'),sign(target,{nbf:Math.floor(Date.now()/1000)+120})])await request(target,'GET',p,token,null,401);
  await request(target,'GET',p,human,null,target==='compras'?403:404);
  const insufficient=sign(target,{token_use:'service',sub:`service:${caller}-to-${target}`,roles:undefined,scope:'wrong.scope',aud:['ticketya-'+target]});
  await request(target,'GET',p,insufficient,null,403);
  const wrongCaller=caller==='eventos'?'entradas':'eventos';
  await request(target,'GET',p,sign(target,{token_use:'service',sub:`service:${wrongCaller}-to-${target}`,roles:undefined,scope,aud:['ticketya-'+target]}),null,403);
  const token=await technical(caller,target);await request(target,'GET',p,token,null,ok);
  await request(target,'GET','/compras/1',token,null,403);
 }
 for(const [caller,targets]of Object.entries(CALLS))for(const target of targets){const token=await technical(caller,target);const response=await request(target,'GET','/internal/security/readiness',token,null,200);assert.equal(response.service,target);}
 // Authenticated administrator has authority independently of all incoming actor headers.
 await request('eventos','PATCH','/api/v1/events/999/cancel',admin,null,404,{'X-Admin-Id':'-1','X-User-Role':'CLIENTE'});
 await request('eventos','PATCH','/api/v1/events/999/cancel',human,null,403,{'X-Admin-Id':'41001','X-User-Role':'ADMIN'});
 console.log(JSON.stringify({checks,requests,technicalLinks:7,financialRegression:'RUN_BY_HOST_ISOLATED_COMMERCIAL_RUNNER',securityEnabled:true}));
}
main().catch(error=>{console.error('Security HTTP check failed: '+String(error.message).replace(/eyJ[A-Za-z0-9_.-]+/g,'[redacted]'));process.exitCode=1;});
