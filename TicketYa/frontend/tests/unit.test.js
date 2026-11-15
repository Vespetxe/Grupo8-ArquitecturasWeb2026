import test, { afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { api, configureSession, hasRole, paymentKey, streamNotifications } from '../src/api.js';
const original = globalThis.fetch;
afterEach(() => { globalThis.fetch = original; configureSession(null); });
test('JWT enviado por encabezado al proxy del Gateway, sin cookies ni token en URL', async () => {
  configureSession('sentinel'); let captured;
  globalThis.fetch = async (...args) => { captured = args; return Response.json({ ok: true }); };
  assert.deepEqual(await api('/auth/me'), { ok: true });
  assert.equal(captured[0], '/gateway/auth/me'); assert.equal(captured[1].headers.Authorization, 'Bearer sentinel'); assert.equal(captured[1].cache, 'no-store'); assert.equal(captured[1].credentials, 'omit');
});
test('login anónimo no reutiliza credenciales de una sesión anterior', async () => {
  configureSession('old'); globalThis.fetch = async (url, options) => { assert.equal(options.headers.Authorization, undefined); return Response.json({}); };
  await api('/auth/login', { method: 'POST', body: {}, anonymous: true });
});
test('401 informa vencimiento, 403 conserva la sesión y no transforma el error en éxito', async () => {
  let expired = 0; configureSession('token', () => expired++);
  globalThis.fetch = async () => new Response('', { status: 403 }); await assert.rejects(api('/compras/1'), e => e.status === 403); assert.equal(expired, 0);
  globalThis.fetch = async () => new Response('', { status: 401 }); await assert.rejects(api('/compras/1'), e => e.status === 401); assert.equal(expired, 1);
});
for (const status of [409, 429, 503]) test(`HTTP ${status} no repite una escritura ni expone mensajes externos`, async () => {
  let calls = 0; globalThis.fetch = async () => { calls++; return Response.json({ message: 'secret-sentinel' }, { status }); };
  await assert.rejects(api('/pagos', { method: 'POST', body: {} }), e => e.status === status && !e.message.includes('secret-sentinel')); assert.equal(calls, 1);
});
test('respuesta perdida o JSON inválido conserva resultado incierto, sin retries', async () => {
  let calls = 0; globalThis.fetch = async () => { calls++; throw Error('secret'); };
  await assert.rejects(api('/pagos', { method: 'POST' }), e => e.status === 0 && !e.message.includes('secret')); assert.equal(calls, 1);
  globalThis.fetch = async () => new Response('invalid'); await assert.rejects(api('/pagos'), e => e.status === 0);
});
test('abortar una lectura no equivale a una confirmación comercial', async () => {
  globalThis.fetch = async () => { throw new DOMException('Aborted', 'AbortError'); }; await assert.rejects(api('/compras/1'), e => e.name === 'AbortError');
});
test('204, cierre de sesión y rutas externas', async () => {
  globalThis.fetch = async (url, options) => { assert.equal(options.headers.Authorization, undefined); return new Response(null, { status: 204 }); };
  configureSession(null); assert.equal(await api('/auth/me'), null); await assert.rejects(api('//foreign.test')); await assert.rejects(api('https://foreign.test'));
});
test('la clave de pago se conserva por compra y propietario', () => {
  const map = new Map(); const storage = { getItem: k => map.get(k), setItem: (k, v) => map.set(k, v) };
  const first = paymentKey(1, 1, storage); assert.equal(first, paymentKey(1, 1, storage)); assert.notEqual(first, paymentKey(2, 1, storage)); assert.notEqual(first, paymentKey(1, 2, storage));
});
test('rutas según roles usan roles explícitos, sin elevar cliente a organizador', () => {
  assert.equal(hasRole(null, 'CLIENTE'), false); assert.equal(hasRole({ roles: ['CLIENTE'] }, 'ORGANIZADOR'), false); assert.equal(hasRole({ roles: ['PERSONAL_ACCESO'] }, 'PERSONAL_ACCESO'), true);
});
test('SSE procesa fragmentos y autentica por encabezado, no query string', async () => {
  configureSession('sentinel'); const controller = new AbortController(); let events = 0;
  globalThis.fetch = async (url, options) => { assert.equal(url, '/gateway/api/v1/me/notifications/stream'); assert.equal(options.headers.Authorization, 'Bearer sentinel'); return new Response(new ReadableStream({ start(c) { const enc = new TextEncoder(); c.enqueue(enc.encode('event: notification\ndata: {')); c.enqueue(enc.encode('}\n\n')); c.close(); } })); };
  await streamNotifications(controller.signal, () => { events++; controller.abort(); }, () => {}); assert.equal(events, 1);
});
test('SSE admite separadores CRLF divididos entre fragmentos', async () => {
  configureSession('sentinel'); const controller = new AbortController(); let events = 0;
  globalThis.fetch = async () => new Response(new ReadableStream({ start(c) { const enc = new TextEncoder(); c.enqueue(enc.encode('data: {}\r')); c.enqueue(enc.encode('\n\r')); c.enqueue(enc.encode('\n')); c.close(); } }));
  await streamNotifications(controller.signal, () => { events++; controller.abort(); }, () => {}); assert.equal(events, 1);
});
