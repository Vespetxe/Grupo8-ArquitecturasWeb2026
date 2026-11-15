'use strict';
const assert=require('node:assert/strict');
const mongo=require('./recommendation-isolation.cjs');
const central=require('./central-isolation.cjs');
function identity(id,port=48087){const m=mongo.identity(id,port),c=central.identity(id,port);return {...m,logPort:38224+(port-48080),logVolume:c.logVolume,logNetwork:c.logNetwork,logSubnet:'10.204.'+parseInt(id.slice(0,2),16)+'.0/24'};}
function spec(meta){
 const s=mongo.spec(mongo.identity(meta.id,meta.port)),c=central.spec(central.identity(meta.id,meta.port));
 for(const name of central.APPS)s.services[name].logging=central.logging(meta);
 s.services.collector=c.services.collector;s.services.collector.ports[0].published=String(meta.logPort);
 s.networks.logs={...c.networks.logs,ipam:{config:[{subnet:meta.logSubnet}]}};s.volumes.logs_data=c.volumes.logs_data;
 return s;
}
function validate(meta,s){assert.deepEqual(meta,identity(meta.id,meta.port));assert.deepEqual(s,spec(meta),'Noncanonical combined Mongo/collector topology');}
function attest(meta,name,c,credentials,images){
 if(name==='collector')return central.attest({...central.identity(meta.id,meta.port),logPort:meta.logPort},name,c,credentials,images);
 mongo.attest(mongo.identity(meta.id,meta.port),name,c,credentials,images);
 if(central.APPS.includes(name))assert.deepEqual(c.HostConfig.LogConfig,{Type:'fluentd',Config:central.logging(meta).options});
}
function assertSubnetFree(meta,networks){mongo.assertSubnetFree(meta,networks);mongo.assertSubnetFree({...meta,subnet:meta.logSubnet},networks);}
module.exports={...mongo,identity,spec,validate,attest,assertSubnetFree,APPS:central.APPS,logging:central.logging,assertEndpointFree:central.assertEndpointFree};
