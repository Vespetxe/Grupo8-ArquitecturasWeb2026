'use strict';
const assert=require('node:assert/strict');
const base=require('./security-isolation.cjs');
const APPS=['gateway','identidad','eventos','inventario','entradas','compras'];
function identity(id,port=48087){return {...base.identity(id,port),logPort:49224+(port-48080),logVolume:`ticketya-e2-reg-${id}_logs_data`,logNetwork:`ticketya-e2-reg-${id}_logs`};}
function logging(meta){return {driver:'fluentd',options:{'fluentd-address':`127.0.0.1:${meta.logPort}`,'fluentd-async':'true','fluentd-async-reconnect-interval':'1s','fluentd-write-timeout':'1s','fluentd-buffer-limit':'1024',mode:'non-blocking','max-buffer-size':'4m','cache-disabled':'false','cache-max-size':'4m','cache-max-file':'2',tag:'ticketya.app'}};}
function spec(meta){
 const s=base.spec(base.identity(meta.id,meta.port));
 for(const name of APPS)s.services[name].logging=logging(meta);
 s.networks.logs={};s.volumes.logs_data={};
 s.services.collector={image:'ticketya-e2-logs:dev',pull_policy:'never',user:'10001:10001',read_only:true,cap_drop:['ALL'],security_opt:['no-new-privileges:true'],pids_limit:32,mem_limit:'192m',cpus:0.5,stop_grace_period:'20s',networks:['logs'],volumes:[{type:'volume',source:'logs_data',target:'/data'}],tmpfs:['/tmp:rw,noexec,nosuid,size=8m,uid=10001,gid=10001'],ports:[{target:24224,published:String(meta.logPort),host_ip:'127.0.0.1',protocol:'tcp'}],logging:{driver:'local',options:{'max-size':'2m','max-file':'2'}},healthcheck:{test:['CMD','/usr/bin/python3','-c',"import urllib.request; urllib.request.urlopen('http://127.0.0.1:9881/health',timeout=2)"],interval:'3s',timeout:'3s',retries:10,start_period:'5s'}};
 return s;
}
function validate(meta,value){assert.deepEqual(meta,identity(meta.id,meta.port));assert.deepEqual(value,spec(meta),'Noncanonical central logging topology');}
function assertEndpointFree(port,containers){for(const c of containers)assert.notEqual(c.HostConfig.LogConfig?.Config?.['fluentd-address'],`127.0.0.1:${port}`,'Logging endpoint already assigned to another container');}
function attest(meta,name,c,credentials,images){
 if(name!=='collector'){
  base.attest(base.identity(meta.id,meta.port),name,c,credentials,images);
  if(APPS.includes(name))assert.deepEqual(c.HostConfig.LogConfig,{Type:'fluentd',Config:logging(meta).options});
  return;
 }
 assert.equal(c.Config.Labels['com.docker.compose.project'],meta.project);assert.equal(c.Config.Labels['com.docker.compose.service'],name);
 assert.equal(c.State.Running,true);assert.equal(c.State.Health.Status,'healthy');assert.equal(c.Image,images.collector);assert.equal(c.Config.User,'10001:10001');
 assert.equal(c.HostConfig.ReadonlyRootfs,true);assert.equal(c.HostConfig.Privileged,false);assert.deepEqual(c.HostConfig.CapDrop,['ALL']);assert.deepEqual(c.HostConfig.SecurityOpt,['no-new-privileges:true']);
 assert.deepEqual(Object.keys(c.NetworkSettings.Networks),[meta.logNetwork]);assert.equal(c.Mounts.length,1);assert.equal(c.Mounts[0].Name,meta.logVolume);assert.equal(c.Mounts[0].Destination,'/data');
 assert.deepEqual(c.HostConfig.PortBindings,{'24224/tcp':[{HostIp:'127.0.0.1',HostPort:String(meta.logPort)}]});
 assert.ok(!c.Config.Env.some(v=>/PASSWORD|SECRET|PRIVATE_KEY|TOKEN=/.test(v)));assert.equal(c.HostConfig.LogConfig.Type,'local');
}
module.exports={...base,identity,spec,validate,attest,APPS,logging,assertEndpointFree};
