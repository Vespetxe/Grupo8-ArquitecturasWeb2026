'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const guard=require('./central-isolation.cjs');
const m=guard.identity('0123456789abcdef',48087);
test('central topology retains strict business isolation and async logging',()=>{
 const s=guard.spec(m);guard.validate(m,s);
 assert.equal(Object.keys(s.services).length,8);
 for(const name of guard.APPS){assert.equal(s.services[name].logging.options['fluentd-async'],'true');assert.ok(!s.services[name].depends_on?.collector);}
 assert.deepEqual(s.services.collector.networks,['logs']);assert.ok(!s.services.collector.environment);
});
test('rejects a logging destination reserved by a stopped producer',()=>{
 const c={State:{Running:false},HostConfig:{LogConfig:{Type:'fluentd',Config:{'fluentd-address':`127.0.0.1:${m.logPort}`}}}};
 assert.throws(()=>guard.assertEndpointFree(m.logPort,[c]));
});
test('accepts independent destinations and local-only historical logging',()=>{
 guard.assertEndpointFree(m.logPort,[{HostConfig:{LogConfig:{Type:'json-file',Config:{}}}},{HostConfig:{LogConfig:{Type:'fluentd',Config:{'fluentd-address':'127.0.0.1:49224'}}}}]);
});
for(const [label,mutate]of [
 ['public collector',s=>s.services.collector.ports[0].host_ip='0.0.0.0'],
 ['socket mount',s=>s.services.collector.volumes.push('/var/run/docker.sock:/var/run/docker.sock')],
 ['persistent database',s=>s.services.compras.environment.DB_URL='jdbc:mysql://mysql:3306/ticketya_e2_docker_compras'],
 ['external archive',s=>s.volumes.logs_data.external=true],
 ['blocking logging',s=>s.services.gateway.logging.options.mode='blocking'],
 ['collector dependency',s=>s.services.gateway.depends_on={collector:{condition:'service_healthy'}}],
 ['root collector',s=>s.services.collector.user='0'],
 ['unbounded archive',s=>s.services.collector.environment={ARCHIVE_CAP_BYTES:'999999999999'}],
])test('reject '+label,()=>{const s=guard.spec(m);mutate(s);assert.throws(()=>guard.validate(m,s));});
