'use strict';
const assert = require('node:assert/strict');

const SERVICES = ['mysql', 'eventos', 'inventario', 'entradas', 'compras', 'gateway'];
const PORTS = {eventos:8081, inventario:8082, entradas:8083, compras:8084, gateway:8080};
const IMAGES = Object.fromEntries(Object.keys(PORTS).map(s => [s, `ticketya-e2-${s === 'gateway' ? 'gateway' : s}:dev`]));
// The inventory image uses the Spanish singular tag already validated in Docker.
IMAGES.inventario = 'ticketya-e2-inventario:dev';

function identity(id, port) {
  assert.match(id, /^[a-f0-9]{16}$/);
  assert.ok(Number.isInteger(port) && port >= 48080 && port <= 48089, 'Puerto fuera de la zona E2');
  const project = `ticketya-e2-reg-${id}`;
  return {id, port, project, volume:`${project}_mysql_data`, network:`${project}_backend`,
    schemas:Object.fromEntries(['eventos','inventario','entradas','compras'].map(s => [s, `ty_e2_reg_${id}_${s}`]))};
}
function composeSpec(meta) {
  identity(meta.id, meta.port);
  const services = {mysql:{image:'mysql:8.4.8', stop_grace_period:'60s',
    environment:{MYSQL_ROOT_PASSWORD:'${TY_REG_ROOT_PASSWORD:?required}'},
    volumes:['mysql_data:/var/lib/mysql'], networks:['backend'],
    healthcheck:{test:['CMD-SHELL','MYSQL_PWD="$${MYSQL_ROOT_PASSWORD}" mysql --protocol=TCP -h127.0.0.1 -uroot --connect-timeout=2 --execute="SELECT 1" >/dev/null 2>&1'], interval:'2s',timeout:'5s',retries:60,start_period:'20s'}}};
  for (const service of Object.keys(PORTS)) {
    const environment = {SERVER_PORT:String(PORTS[service]), SPRING_PROFILES_ACTIVE:'integracion'};
    if (service !== 'gateway') Object.assign(environment, {
      DB_URL:`jdbc:mysql://mysql:3306/${meta.schemas[service]}?allowPublicKeyRetrieval=true&useSSL=false`,
      DB_USERNAME:`reg_${service}`, DB_PASSWORD:`\${TY_REG_${service.toUpperCase()}_PASSWORD:?required}`,
      SPRING_JPA_HIBERNATE_DDL_AUTO:'update'});
    const targets = service === 'gateway' ? ['eventos','inventario','entradas','compras']
      : service === 'eventos' ? ['inventario','entradas','compras']
      : service === 'compras' ? ['eventos','inventario','entradas'] : service === 'entradas' ? ['compras'] : [];
    for (const target of targets) environment[`${target.toUpperCase()}_BASE_URL`] = `http://${target}:${PORTS[target]}`;
    services[service] = {image:IMAGES[service],pull_policy:'never',environment,networks:['backend'],
      depends_on:{mysql:{condition:'service_healthy'}}};
    if (service === 'gateway') services[service].ports = [{target:8080,published:String(meta.port),host_ip:'127.0.0.1',protocol:'tcp'}];
  }
  return {name:meta.project,services,volumes:{mysql_data:{}},networks:{backend:{}}};
}
function validateSpec(meta, spec) {
  assert.deepEqual(meta, identity(meta.id, meta.port), 'Identidad alterada');
  assert.deepEqual(spec, composeSpec(meta), 'Compose alterado: no se permiten overrides ni recursos externos');
}
function assertContainer(meta, service, c, credentials) {
  assert.ok(SERVICES.includes(service));
  assert.equal(c.Config.Labels['com.docker.compose.project'],meta.project);
  assert.equal(c.Config.Labels['com.docker.compose.service'],service);
  assert.equal(c.State.Running,true,'Contenedor no running');
  assert.equal(c.RestartCount,0,'Reinicio inesperado');
  assert.deepEqual(Object.keys(c.NetworkSettings.Networks),[meta.network]);
  const env = Object.fromEntries(c.Config.Env.map(v => {const p=v.indexOf('=');return [v.slice(0,p),v.slice(p+1)];}));
  const expectedEnv=composeSpec(meta).services[service].environment;
  for(const [key,value] of Object.entries(expectedEnv)) {
    const expected=value.startsWith('${TY_REG_') ? (key==='MYSQL_ROOT_PASSWORD'?credentials.root:credentials[service]) : value;
    assert.equal(env[key],expected,`Entorno efectivo incompatible: ${key}`);
  }
  for(const key of ['SPRING_APPLICATION_JSON','SPRING_CONFIG_LOCATION','SPRING_CONFIG_ADDITIONAL_LOCATION','SPRING_CONFIG_IMPORT','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'])assert.ok(!(key in env),`Override no permitido: ${key}`);
  const bindings=c.HostConfig.PortBindings || {};
  if(service === 'mysql') {
    assert.equal(c.Config.Image,'mysql:8.4.8');
    assert.equal(c.State.Health.Status,'healthy');
    assert.equal(env.MYSQL_ROOT_PASSWORD,credentials.root,'Credencial de instancia inesperada');
    assert.equal(c.Mounts.length,1);
    assert.equal(c.Mounts[0].Type,'volume');
    assert.equal(c.Mounts[0].Name,meta.volume);
    assert.equal(c.Mounts[0].Destination,'/var/lib/mysql');
  } else {
    assert.equal(c.Config.Image,IMAGES[service]);
    assert.deepEqual(c.Config.Entrypoint,['java','-jar','/app/app.jar']);
    assert.ok(!c.Config.Cmd || c.Config.Cmd.length===0,'Argumentos de arranque inesperados');
    assert.equal(c.Mounts.length,0,'Montaje de aplicacion inesperado');
    assert.equal(env.SPRING_PROFILES_ACTIVE,'integracion');
    if(service !== 'gateway') {
      assert.equal(env.DB_URL,`jdbc:mysql://mysql:3306/${meta.schemas[service]}?allowPublicKeyRetrieval=true&useSSL=false`);
      assert.equal(env.DB_USERNAME,`reg_${service}`);
      assert.equal(env.DB_PASSWORD,credentials[service],'Credencial de esquema inesperada');
      assert.equal(env.SPRING_JPA_HIBERNATE_DDL_AUTO,'update');
      assert.ok(!('SPRING_DATASOURCE_URL' in env),'Override JDBC no permitido');
    }
  }
  if(service === 'gateway') {
    const expected={'8080/tcp':[{HostIp:'127.0.0.1',HostPort:String(meta.port)}]};
    assert.deepEqual(bindings,expected);
    assert.deepEqual(c.NetworkSettings.Ports,expected,'Puerto solicitado pero no publicado efectivamente');
  } else {
    assert.equal(Object.keys(bindings).length,0,'Puerto publicado inesperado');
    assert.ok(Object.values(c.NetworkSettings.Ports||{}).every(value=>!value||value.length===0),'Publicacion efectiva inesperada');
  }
}
function assertVolume(meta, volume) {
  assert.equal(volume.Name,meta.volume);
  assert.equal(volume.Labels['com.docker.compose.project'],meta.project);
  assert.equal(volume.Labels['com.docker.compose.volume'],'mysql_data');
}
function urlFor(meta, requestPath) {
  identity(meta.id,meta.port);
  assert.ok(requestPath.startsWith('/') && !requestPath.startsWith('//') && !requestPath.includes('\\'));
  assert.ok(!requestPath.includes('://') && !requestPath.startsWith('/internal/'));
  const base=`http://127.0.0.1:${meta.port}`;
  const url=new URL(requestPath,base);
  assert.equal(url.origin,base);
  assert.ok(!url.pathname.startsWith('/internal/'));
  return url;
}
module.exports={SERVICES,PORTS,IMAGES,identity,composeSpec,validateSpec,assertContainer,assertVolume,urlFor};
