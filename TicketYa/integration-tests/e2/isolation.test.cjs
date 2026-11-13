'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {identity,composeSpec,validateSpec,assertContainer,assertVolume,urlFor}=require('./isolation.cjs');
const meta=identity('0123456789abcdef',48080);
const credentials={root:'root-test',eventos:'eventos-test',inventario:'inventario-test',entradas:'entradas-test',compras:'compras-test'};
const clone=x=>JSON.parse(JSON.stringify(x));
function container(service){
  const spec=composeSpec(meta).services[service];
  const env={...spec.environment};
  if(service==='mysql')env.MYSQL_ROOT_PASSWORD=credentials.root;
  else if(service!=='gateway')env.DB_PASSWORD=credentials[service];
  return {Config:{Image:spec.image,Entrypoint:['java','-jar','/app/app.jar'],Cmd:null,Labels:{'com.docker.compose.project':meta.project,'com.docker.compose.service':service},Env:Object.entries(env).map(([k,v])=>`${k}=${v}`)},
    State:{Running:true,Health:{Status:'healthy'}},RestartCount:0,
    NetworkSettings:{Networks:{[meta.network]:{}},Ports:service==='gateway'?{'8080/tcp':[{HostIp:'127.0.0.1',HostPort:'48080'}]}:{}},
    HostConfig:{PortBindings:service==='gateway'?{'8080/tcp':[{HostIp:'127.0.0.1',HostPort:'48080'}]}:{}},
    Mounts:service==='mysql'?[{Type:'volume',Name:meta.volume,Destination:'/var/lib/mysql'}]:[]};
}
test('acepta exclusivamente el Compose canonico aislado',()=>validateSpec(meta,composeSpec(meta)));
for(const port of [38080,8080,3306,33079,33489,33479,28080,48079,48090,NaN]){
  test(`rechaza puerto fuera del rango E2: ${port}`,()=>assert.throws(()=>identity(meta.id,port)));
}
test('rechaza proyecto persistente aun con un esquema aparentemente de prueba',()=>{
  const bad={...meta,project:'ticketya-e2-infra'};assert.throws(()=>validateSpec(bad,composeSpec(bad)));
});
for(const db of ['ticketya_e2_docker_compras','ticketya_compras','ticketya_e1_compras']){
  test(`rechaza JDBC persistente o de E1: ${db}`,()=>{
    const bad=clone(composeSpec(meta));bad.services.compras.environment.DB_URL=`jdbc:mysql://mysql:3306/${db}`;
    assert.throws(()=>validateSpec(meta,bad));
  });
}
test('rechaza volumen externo, bind y montaje de datos existentes',()=>{
  for(const mutate of [s=>s.volumes.mysql_data={external:true},s=>s.services.mysql.volumes=['C:/datos:/var/lib/mysql'],s=>s.services.mysql.volumes=['ticketya-e2-infra_mysql_data:/var/lib/mysql']]){
    const bad=clone(composeSpec(meta));mutate(bad);assert.throws(()=>validateSpec(meta,bad));
  }
});
test('rechaza red externa, host network y override de entrada',()=>{
  for(const mutate of [s=>s.networks.backend={external:true},s=>s.services.compras.network_mode='host',s=>s.services.compras.entrypoint=['otro']]){
    const bad=clone(composeSpec(meta));mutate(bad);assert.throws(()=>validateSpec(meta,bad));
  }
});
test('acredita contenedores efectivos y credenciales de la instancia nueva',()=>{
  for(const s of ['mysql','eventos','inventario','entradas','compras','gateway'])assertContainer(meta,s,container(s),credentials);
});
test('rechaza contenedor efectivo conectado a la base persistente',()=>{
  const c=container('compras');c.Config.Env=c.Config.Env.map(x=>x.startsWith('DB_URL=')?'DB_URL=jdbc:mysql://mysql:3306/ticketya_e2_docker_compras':x);
  assert.throws(()=>assertContainer(meta,'compras',c,credentials));
});
test('rechaza suplantacion del puerto, proyecto y montajes efectivos',()=>{
  const c=container('gateway');c.HostConfig.PortBindings['8080/tcp'][0].HostPort='38080';assert.throws(()=>assertContainer(meta,'gateway',c,credentials));
  const m=container('mysql');m.Mounts[0].Name='ticketya-e2-infra_mysql_data';assert.throws(()=>assertContainer(meta,'mysql',m,credentials));
  const p=container('entradas');p.Config.Labels['com.docker.compose.project']='ticketya-e2-infra';assert.throws(()=>assertContainer(meta,'entradas',p,credentials));
});
test('rechaza override Spring del datasource y redes adicionales',()=>{
  const c=container('compras');c.Config.Env.push('SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/ticketya_compras');assert.throws(()=>assertContainer(meta,'compras',c,credentials));
  const n=container('entradas');n.NetworkSettings.Networks.persistente={};assert.throws(()=>assertContainer(meta,'entradas',n,credentials));
});
test('rechaza URL REST saliente persistente y argumentos que cambien configuracion',()=>{
  const c=container('compras');c.Config.Env=c.Config.Env.map(v=>v.startsWith('ENTRADAS_BASE_URL=')?'ENTRADAS_BASE_URL=http://host.docker.internal:8083':v);
  assert.throws(()=>assertContainer(meta,'compras',c,credentials));
  const d=container('inventario');d.Config.Cmd=['--spring.datasource.url=jdbc:mysql://host.docker.internal/prod'];assert.throws(()=>assertContainer(meta,'inventario',d,credentials));
});
test('rechaza volumen con etiquetas ajenas',()=>assert.throws(()=>assertVolume(meta,{Name:meta.volume,Labels:{'com.docker.compose.project':'ticketya-e2-infra'}})));
test('rechaza puerto solicitado que Docker no publico efectivamente',()=>{
  const c=container('gateway');c.NetworkSettings.Ports={'8080/tcp':[]};assert.throws(()=>assertContainer(meta,'gateway',c,credentials));
});
test('URL fija a loopback y puerto propio; sin endpoints internos ni escapes',()=>{
  assert.equal(urlFor(meta,'/compras?clienteId=1').origin,'http://127.0.0.1:48080');
  for(const p of ['http://127.0.0.1:38080/compras','//127.0.0.1:38080/compras','/internal/tickets/issue','/../internal/orders/1','/\\localhost/compras'])assert.throws(()=>urlFor(meta,p));
});
