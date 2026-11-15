'use strict';
const assert=require('node:assert/strict'),path=require('node:path');
const base=require('./security-isolation.cjs');
function identity(id,port=48080){return {...base.identity(id,port),mongoDatabase:'ty_e2_preferences_'+id,mongoVolume:'ticketya-e2-reg-'+id+'_mongo_data',subnet:'10.203.'+parseInt(id.slice(0,2),16)+'.0/24'};}
function uri(meta,password){return `mongodb://ty_eventos_preferences:${password}@mongo:27017/${meta.mongoDatabase}?authSource=${meta.mongoDatabase}&serverSelectionTimeoutMS=1000&connectTimeoutMS=1000&socketTimeoutMS=1000&waitQueueTimeoutMS=500&maxPoolSize=10&retryReads=false&retryWrites=false`;}
const root=path.resolve(__dirname,'../../..');
function spec(meta){
 const s=base.spec(base.identity(meta.id,meta.port));s.volumes.mongo_data={};s.networks.backend.ipam={config:[{subnet:meta.subnet}]};
 s.services.mongo={image:'mongo:8.0.16',command:['mongod','--bind_ip_all','--wiredTigerCacheSizeGB','0.25'],mem_limit:'768m',networks:['backend'],environment:{MONGO_INITDB_ROOT_USERNAME:'ty_mongo_admin',MONGO_INITDB_ROOT_PASSWORD:'${TY_REG_MONGO_ROOT_PASSWORD:?required}',MONGO_INITDB_DATABASE:meta.mongoDatabase,TY_E2_EVENTOS_MONGO_PASSWORD:'${TY_REG_MONGO_EVENTOS_PASSWORD:?required}'},volumes:[{type:'volume',source:'mongo_data',target:'/data/db'},...['01-create-user.js','healthcheck.js'].map((name,index)=>({type:'bind',source:path.join(root,'docker/mongo',name),target:index===0?'/docker-entrypoint-initdb.d/01-create-user.js':'/opt/ticketya/healthcheck.js',read_only:true,bind:{create_host_path:false}}))],tmpfs:['/data/configdb:rw,noexec,nosuid,size=16m'],healthcheck:{test:['CMD','mongosh','--quiet','--file','/opt/ticketya/healthcheck.js'],interval:'3s',timeout:'5s',retries:30,start_period:'15s'}};
 s.services.eventos.environment.MONGODB_URI=uri(meta,'${TY_REG_MONGO_EVENTOS_PASSWORD:?required}');return s;
}
function validate(meta,value){assert.deepEqual(meta,identity(meta.id,meta.port));assert.deepEqual(value,spec(meta),'Noncanonical Mongo test topology');}
function attest(meta,name,c,credentials,images){
 if(name!=='mongo'){
  base.attest(base.identity(meta.id,meta.port),name,c,credentials,images);
  const env=Object.fromEntries(c.Config.Env.map(v=>[v.slice(0,v.indexOf('=')),v.slice(v.indexOf('=')+1)]));
  if(name==='eventos')assert.equal(env.MONGODB_URI,uri(meta,credentials.TY_REG_MONGO_EVENTOS_PASSWORD));else assert.equal(env.MONGODB_URI,undefined);
  return;
 }
 assert.equal(c.Config.Labels['com.docker.compose.project'],meta.project);assert.equal(c.Config.Labels['com.docker.compose.service'],'mongo');assert.equal(c.Image,images.mongo);assert.equal(c.State.Running,true);assert.equal(c.State.Health.Status,'healthy');assert.equal(c.RestartCount,0);assert.equal(c.HostConfig.Memory,768*1024*1024);assert.deepEqual(Object.keys(c.NetworkSettings.Networks),[meta.network]);assert.equal(Object.keys(c.HostConfig.PortBindings||{}).length,0);
 const data=c.Mounts.filter(m=>m.Type==='volume');assert.equal(data.length,1);assert.equal(data[0].Name,meta.mongoVolume);assert.equal(data[0].Destination,'/data/db');
 const binds=c.Mounts.filter(m=>m.Type==='bind');assert.equal(binds.length,2);for(const expected of spec(meta).services.mongo.volumes.filter(v=>v.type==='bind')){const actual=binds.find(m=>m.Destination===expected.target);assert.ok(actual);assert.equal(actual.RW,false);assert.equal(path.resolve(actual.Source).toLowerCase(),path.resolve(expected.source).toLowerCase());}
 const env=Object.fromEntries(c.Config.Env.map(v=>[v.slice(0,v.indexOf('=')),v.slice(v.indexOf('=')+1)]));for(const [k,v]of Object.entries(spec(meta).services.mongo.environment)){const match=v.match(/^\$\{([^:]+):/);assert.equal(env[k],match?credentials[match[1]]:v);}
}
function assertSubnetFree(meta,networks){
 const range=cidr=>{const [address,prefix]=cidr.split('/');const bytes=address.split('.').map(Number);assert.equal(bytes.length,4);const size=2**(32-Number(prefix)),start=Math.floor(bytes.reduce((n,b)=>n*256+b,0)/size)*size;return [start,start+size-1];};
 const [start,end]=range(meta.subnet);for(const network of networks)for(const config of network.IPAM?.Config||[]){if(!config.Subnet||config.Subnet.includes(':'))continue;const [first,last]=range(config.Subnet);assert.ok(end<first||start>last,'Isolated subnet overlaps an existing Docker network');}
}
module.exports={...base,identity,spec,validate,attest,uri,assertSubnetFree};
