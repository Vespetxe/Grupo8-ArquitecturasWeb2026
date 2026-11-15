'use strict';
const assert=require('node:assert/strict'),path=require('node:path');
const legacy=require('./isolation.cjs');
const CALLS={eventos:['inventario','entradas','compras'],compras:['eventos','inventario','entradas'],entradas:['compras']};
const SCOPES={'eventos:inventario':'inventory.prepare inventory.lifecycle reports.inventory.read','eventos:entradas':'tickets.event-block reports.tickets.read','eventos:compras':'purchases.event-cancel purchases.history.read notifications.publish reports.sales.read','compras:eventos':'events.read','compras:inventario':'reservations.read reservations.confirm reservations.release reservations.compensate reservations.return','compras:entradas':'tickets.read tickets.issue tickets.annul tickets.status','entradas:compras':'orders.read'};
const root=path.resolve(__dirname,'../../..');
function identity(id,port=48082){const meta=legacy.identity(id,port);return {...meta,identitySchema:`ty_e2_identity_${id}`,directory:path.join(root,'TicketYa/integration-tests/target/security',id)};}
function spec(meta){
 const old=legacy.identity(meta.id,meta.port),s=legacy.composeSpec(old);
 for(const name of ['eventos','inventario','entradas','compras','gateway']){
  Object.assign(s.services[name].environment,{TY_SECURITY_ISSUER:'http://identidad:8085',TY_SECURITY_KEY_ID:`security-${meta.id}`,TY_SECURITY_PUBLIC_KEY:'/run/verification/public.pem'});
  s.services[name].volumes=[{type:'bind',source:path.join(meta.directory,'keys/public.pem'),target:'/run/verification/public.pem',read_only:true,bind:{create_host_path:false}}];
  if(CALLS[name]){
   s.services[name].environment.TY_SECURITY_TOKEN_URL='http://identidad:8085/oauth2/token';
   for(const target of CALLS[name])s.services[name].environment[`TY_SECURITY_${target.toUpperCase()}_SECRET`]=`\${TY_REG_${name.toUpperCase()}_${target.toUpperCase()}_SECRET:?required}`;
  }
 }
 s.services.gateway.environment.IDENTIDAD_BASE_URL='http://identidad:8085';
 s.services.identidad={image:'ticketya-e2-identidad:dev',pull_policy:'never',networks:['backend'],environment:{
  SERVER_PORT:'8085',SPRING_PROFILES_ACTIVE:'integracion',DB_URL:`jdbc:mysql://mysql:3306/${meta.identitySchema}?allowPublicKeyRetrieval=true&useSSL=false`,DB_USERNAME:'ty_identidad',DB_PASSWORD:'${TY_REG_IDENTIDAD_PASSWORD:?required}',
  SPRING_JPA_HIBERNATE_DDL_AUTO:'update',IDENTITY_JWT_ISSUER:'http://identidad:8085',IDENTITY_JWT_KEY_ID:`security-${meta.id}`,
  IDENTITY_JWT_AUDIENCES:'ticketya-identidad,ticketya-gateway,ticketya-eventos,ticketya-inventario,ticketya-entradas,ticketya-compras',
  IDENTITY_JWT_PRIVATE_KEY:'/keys/private.pem',IDENTITY_JWT_PUBLIC_KEY:'/keys/public.pem'},
  volumes:[{type:'bind',source:path.join(meta.directory,'keys'),target:'/keys',read_only:true,bind:{create_host_path:false}}],depends_on:{mysql:{condition:'service_healthy'}}};
 return s;
}
function validate(meta,value){assert.deepEqual(meta,identity(meta.id,meta.port));assert.deepEqual(value,spec(meta),'Noncanonical security topology');}
function attest(meta,name,c,credentials,images){
 assert.equal(c.Config.Labels['com.docker.compose.project'],meta.project);assert.equal(c.Config.Labels['com.docker.compose.service'],name);
 assert.equal(c.State.Running,true);assert.equal(c.RestartCount,0);assert.equal(c.Image,images[name]);
 assert.deepEqual(Object.keys(c.NetworkSettings.Networks),[meta.network]);
 const expected=spec(meta).services[name];assert.equal(c.Config.Image,expected.image);
 const env=Object.fromEntries(c.Config.Env.map(v=>{const p=v.indexOf('=');return [v.slice(0,p),v.slice(p+1)];}));
 for(const [key,value]of Object.entries(expected.environment)){
  const match=value.match(/^\$\{([^:]+):/);assert.equal(env[key],match?credentials[match[1]]:value,`Unexpected environment ${key}`);
 }
 for(const key of ['SPRING_APPLICATION_JSON','SPRING_CONFIG_LOCATION','SPRING_CONFIG_ADDITIONAL_LOCATION','SPRING_CONFIG_IMPORT','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','SPRING_DATASOURCE_URL'])assert.ok(!(key in env));
 if(name==='mysql'){
  assert.equal(c.State.Health.Status,'healthy');assert.equal(c.Mounts.length,1);assert.equal(c.Mounts[0].Type,'volume');assert.equal(c.Mounts[0].Name,meta.volume);assert.equal(c.Mounts[0].Destination,'/var/lib/mysql');
 }else{
  assert.equal(c.Config.User,'10001:10001');assert.deepEqual(c.Config.Entrypoint,['java','-jar','/app/app.jar']);assert.ok(!c.Config.Cmd||c.Config.Cmd.length===0);
  assert.equal(c.Mounts.length,1);assert.equal(c.Mounts[0].Type,'bind');assert.equal(c.Mounts[0].RW,false);assert.equal(path.resolve(c.Mounts[0].Source).toLowerCase(),path.resolve(expected.volumes[0].source).toLowerCase());assert.equal(c.Mounts[0].Destination,expected.volumes[0].target);
 }
 const ports=c.HostConfig.PortBindings||{};
 if(name==='gateway'){
  const p={'8080/tcp':[{HostIp:'127.0.0.1',HostPort:String(meta.port)}]};assert.deepEqual(ports,p);assert.deepEqual(c.NetworkSettings.Ports,p);
 }else{assert.equal(Object.keys(ports).length,0);assert.ok(Object.values(c.NetworkSettings.Ports||{}).every(v=>!v||v.length===0));}
}
module.exports={CALLS,SCOPES,identity,spec,validate,attest};
