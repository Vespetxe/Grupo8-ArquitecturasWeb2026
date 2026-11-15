'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),guard=require('./recommendation-isolation.cjs');
const meta=guard.identity('1234567890abcdef',48080);
test('Mongo belongs only to Events and its isolated named volume',()=>{const s=guard.spec(meta);guard.validate(meta,s);assert.equal(Object.keys(s.services).length,8);assert.equal(s.services.mongo.ports,undefined);assert.equal(s.services.mongo.environment.MONGO_INITDB_DATABASE,meta.mongoDatabase);assert.ok(s.services.eventos.environment.MONGODB_URI.includes(meta.mongoDatabase));for(const [name,v]of Object.entries(s.services))if(name!=='eventos')assert.equal(v.environment?.MONGODB_URI,undefined);});
test('Reject persistent databases, shared volumes and host port publication',()=>{for(const change of [s=>s.services.eventos.environment.MONGODB_URI='mongodb://localhost/ticketya_e2_preferences',s=>s.services.mongo.ports=['27017:27017'],s=>s.volumes.mongo_data.external=true,s=>s.services.mongo.volumes[0].source='ticketya-e2-infra_mysql_data']){const s=guard.spec(meta);change(s);assert.throws(()=>guard.validate(meta,s));}});
test('Events startup does not depend on Mongo health',()=>assert.equal(guard.spec(meta).services.eventos.depends_on?.mongo,undefined));

test('explicit isolated subnet rejects overlaps without changing existing networks',()=>{
 const meta=guard.identity('abcd123456789abc',48080);
 assert.equal(guard.spec(meta).networks.backend.ipam.config[0].subnet,meta.subnet);
 assert.doesNotThrow(()=>guard.assertSubnetFree(meta,[{IPAM:{Config:[{Subnet:'172.19.0.0/16'}]}}]));
 assert.throws(()=>guard.assertSubnetFree(meta,[{IPAM:{Config:[{Subnet:'10.203.0.0/16'}]}}]));
});
