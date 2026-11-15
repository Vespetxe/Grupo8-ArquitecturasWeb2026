import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const guard = require('../../integration-tests/e2/recommendation-isolation.cjs');
export async function fixture() {
  const project = process.env.TY_FRONTEND_TEST_PROJECT;
  assert.match(project || '', /^ticketya-e2-reg-[a-f0-9]{16}$/,'Explicit isolated project required');
  const run = project.slice('ticketya-e2-reg-'.length); const meta = guard.identity(run, 48080);
  const specFile = path.join(meta.directory, 'compose.json'); const spec = JSON.parse(fs.readFileSync(specFile)); guard.validate(meta, spec);
  const docker = args => execFileSync('docker', args, { encoding: 'utf8', timeout: 30000, maxBuffer: 8 * 1024 * 1024, stdio: ['pipe','pipe','pipe'] });
  // Read lifecycle metadata only for historical containers, never their environments.
  const snapshot = () => docker(['inspect','--format','{{.Id}}|{{.State.Status}}|{{.State.StartedAt}}|{{.RestartCount}}|{{index .Config.Labels "com.docker.compose.project"}}', ...docker(['ps','-aq']).trim().split(/\s+/)]).trim().split(/\r?\n/).filter(line => line.split('|').at(-1) !== project);
  const previous = snapshot(); const env = { ...process.env }; for (const k of Object.keys(env)) if (k.startsWith('COMPOSE_') || k.startsWith('TY_REG_')) delete env[k];
  for (const name of Object.keys(spec.services)) {
    const c = JSON.parse(docker(['inspect', `${project}-${name}-1`]))[0]; assert.equal(c.Config.Labels['com.docker.compose.project'], project); assert.equal(c.State.Running, true); assert.equal(c.RestartCount, 0);
    const actual = Object.fromEntries(c.Config.Env.map(v => [v.slice(0,v.indexOf('=')),v.slice(v.indexOf('=')+1)]));
    if (name === 'mysql') { assert.equal(c.State.Health.Status, 'healthy'); assert.ok(c.Mounts.some(m => m.Name === meta.volume)); }
    if (['eventos','inventario','entradas','compras'].includes(name)) { assert.ok(actual.DB_URL.includes(meta.schemas[name])); assert.ok(actual.DB_URL.includes('mysql:3306/')); }
    if (name === 'gateway') assert.deepEqual(c.HostConfig.PortBindings['8080/tcp'], [{ HostIp:'127.0.0.1', HostPort:'48080' }]);
    else assert.equal(Object.keys(c.HostConfig.PortBindings || {}).length,0);
    for (const [key, template] of Object.entries(spec.services[name].environment || {})) {
      const match = template.match(/\$\{(TY_REG_[A-Z_]+):\?[^}]+\}/); if (!match) continue;
      const [before, after] = template.split(match[0]); const value = actual[key]?.slice(before.length, after.length ? -after.length : undefined);
      assert.ok(/^[a-f0-9]{64}$/.test(value || ''), 'Isolated process credential unavailable'); env[match[1]] = value;
    }
  }
  const runId = crypto.randomBytes(6).toString('hex'); const directory = path.resolve('test-results/fixture',runId); fs.mkdirSync(directory, { recursive: true });
  if (process.platform === 'win32') { const account = execFileSync('whoami',[],{encoding:'utf8'}).trim(); execFileSync('icacls.exe',[directory,'/inheritance:r','/grant:r',account+':(OI)(CI)F'],{stdio:'pipe'}); }
  async function call(route, method = 'GET', body, token) {
    const r = await fetch('http://127.0.0.1:48080' + route, { method, signal: AbortSignal.timeout(25000), headers: { ...(token ? { Authorization:'Bearer '+token } : {}), ...(body !== undefined ? {'Content-Type':'application/json'} : {}) }, ...(body !== undefined ? {body:JSON.stringify(body)} : {}) });
    if (!r.ok) throw Error('Isolated setup request failed: '+method+' '+route+' HTTP '+r.status);
    return r.status === 204 ? null : r.json();
  }
  async function account(role) {
    const user = { email:`frontend-${role.toLowerCase()}-${runId}@example.test`, password:crypto.randomBytes(24).toString('hex') };
    if (role === 'CLIENTE') await call('/auth/register','POST',user);
    else { const file = path.join(directory, role+'.properties'); fs.writeFileSync(file,`operation=user\nemail=${user.email}\npassword=${user.password}\nrole=${role}\n`,{mode:0o600,flag:'wx'});
      try { execFileSync('docker',['compose','--env-file',process.platform==='win32'?'NUL':'/dev/null','--project-name',project,'--file',specFile,'run','--no-deps','--volume',file+':/run/frontend.properties:ro','--env','IDENTITY_PROVISION_FILE=/run/frontend.properties','identidad','provision'],{env,stdio:'pipe',timeout:120000}); } catch { throw Error('Isolated identity provisioning failed; credentials redacted'); }
    }
    const login = await call('/auth/login','POST',user); const me = await call('/auth/me','GET',undefined,login.access_token); return {...user,...me,token:login.access_token};
  }
  const organizer = await account('ORGANIZADOR'); const access = await account('PERSONAL_ACCESO'); const client = await account('CLIENTE');
  const venue = await call('/api/v1/venues','POST',{name:'Frontend '+runId,address:'Fixture aislada',city:'Tandil',latitude:-37.32,longitude:-59.13},organizer.token);
  const sector = await call(`/api/v1/venues/${venue.venueId}/sectors`,'POST',{name:'General frontend',type:'NO_NUMERADO',capacity:2},organizer.token);
  const event = await call('/api/v1/events','POST',{name:'Encuentro frontend '+runId,eventType:'Recital',organizerId:organizer.id,venueId:venue.venueId,startDateTime:new Date(Date.now()+8*86400000).toISOString()},organizer.token);
  await call(`/api/v1/events/${event.eventId}/prices`,'POST',[{sectorId:sector.sectorId,price:15000}],organizer.token);
  await call(`/api/v1/events/${event.eventId}/publish`,'PATCH',undefined,organizer.token);
  return { project,client,organizer,access,event,sector,call,verify:()=>assert.deepEqual(snapshot(),previous),directory };
}
