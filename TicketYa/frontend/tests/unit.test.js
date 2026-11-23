import test, { afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { api, ApiError, configureSession, hasRole, paymentKey, recoverPayment, streamNotifications, money, userErrorMessage, errorReference } from '../src/api.js';
const original = globalThis.fetch;
const reference='77f61506-0b95-49f2-aea8-205fd5c17592';
for(const [path,status,body,reason,expected] of [
 ['/reservas',400,{mensaje:'La reserva supera el limite de entradas por cliente y evento',exitoso:false},'ENTRY_LIMIT',/límite de 6 entradas/],
 ['/compras',400,{status:400,message:'La compra supera el limite de entradas permitido por cliente y evento'},'ENTRY_LIMIT',/límite de 6 entradas/],
 ['/reservas',409,{mensaje:'Capacidad o aforo insuficiente',exitoso:false},'INSUFFICIENT_STOCK',/No quedan entradas suficientes/],
 ['/reservas',409,{mensaje:'Uno o más asientos seleccionados ya no se encuentran disponibles'},'SEATS_UNAVAILABLE',/asientos ya no están disponibles/],
 ['/pagos',409,{message:'La reserva se encuentra vencida'},'RESERVATION_EXPIRED',/La reserva venció/],
 ['/compras',400,{message:'La reserva no es valida para el cliente y evento indicados'},'RESERVATION_UNAVAILABLE',/puede haber vencido/],
 ['/pagos',400,{message:'La reserva no es valida, esta vencida o no corresponde a la compra'},'RESERVATION_UNAVAILABLE',/puede haber vencido/],
 ['/reservas',409,{mensaje:'Debe ingresar a la cola'},'QUEUE_REQUIRED',/fila virtual/],
 ['/reservas',409,{mensaje:'Turno no habilitado o vencido; consulte su estado'},'QUEUE_NOT_READY',/turno todavía no está habilitado o venció/]
])test('error específico sin exponer cuerpo ni repetir POST: '+reason+' '+path,async()=>{
 let calls=0;const request={cantidad:7,importe:'28000.50'};configureSession('sentinel');
 globalThis.fetch=async(url,options)=>{calls++;assert.deepEqual(JSON.parse(options.body),request);return Response.json({...body,debug:'SQL-secret-sentinel'},{status,headers:{'X-Correlation-Id':reference}});};
 await assert.rejects(api(path,{method:'POST',body:request}),error=>error.status===status&&error.reason===reason&&expected.test(userErrorMessage(error))&&!error.message.includes('SQL-secret-sentinel')&&errorReference(error)===reference);
 assert.equal(calls,1);assert.equal(request.importe,'28000.50');
});
for(const [path,method,status,body]of [
 ['/reservas','POST',400,{mensaje:'Solicitud invalida'}],
 ['/reservas','POST',409,{mensaje:'Conflicto con un registro existente'}],
 ['/reservas','POST',500,{mensaje:'La reserva supera el limite de entradas por cliente y evento'}],
 ['/reservas','POST',400,{status:403,mensaje:'La reserva supera el limite de entradas por cliente y evento'}],
 ['/reservas','POST',400,{exitoso:true,mensaje:'La reserva supera el limite de entradas por cliente y evento'}],
 ['/pagos','POST',400,{mensaje:'La reserva supera el limite de entradas por cliente y evento'}],
 ['/reservas','GET',400,{mensaje:'La reserva supera el limite de entradas por cliente y evento'}],
 ['/reservas','POST',400,{mensaje:'La reserva supera el limite de entradas por cliente y evento SQL password=sentinel'}],
 ['/reservas','POST',409,{message:'org.hibernate.Exception SELECT secret FROM database'}],
 ['/reservas','POST',400,[{mensaje:'La reserva supera el limite de entradas por cliente y evento'}]]
])test('no adivina causas ni refleja datos remotos: '+JSON.stringify([path,method,status,body]),async()=>{
 globalThis.fetch=async()=>Response.json(body,{status});await assert.rejects(api(path,{method}),e=>e.reason===undefined&&!/sentinel|hibernate|SELECT|límite de 6/.test(e.message));
});
test('errores malformados, HTML, grandes o perdidos mantienen HTTP y no reintentan',async()=>{
 for(const [text,type]of [['<script>secret</script>','text/html'],['invalid','application/json'],['x'.repeat(4097),'application/json']]){
  let calls=0;globalThis.fetch=async()=>{calls++;return new Response(text,{status:409,headers:{'Content-Type':type}});};await assert.rejects(api('/reservas',{method:'POST'}),e=>e.status===409&&e.reason===undefined&&!e.message.includes('secret'));assert.equal(calls,1);
 }
 globalThis.fetch=async()=>({ok:false,status:400,headers:new Headers({'Content-Type':'application/json'}),async text(){throw Error('secret');}});await assert.rejects(api('/reservas',{method:'POST'}),e=>e.status===400&&!e.message.includes('secret'));
});
test('referencias solo UUID; errores inesperados no exponen excepciones',()=>{
 assert.equal(errorReference(new ApiError(409,reference)),reference);for(const value of ['Bearer secret','<script>','a'.repeat(500),null])assert.equal(errorReference(new ApiError(400,value)),undefined);
 assert.ok(!userErrorMessage(new Error('SQL password=secret')).includes('secret'));assert.equal(errorReference({correlation:'secret'}),undefined);
});
test('ARS centralizado: miles, centavos, cero, negativos y números decimales como string',()=>{
 const visible=v=>money(v).replaceAll('\u00a0',' ');
 assert.equal(visible(28000),'$ 28.000');assert.equal(visible(1234.56),'$ 1.234,56');assert.equal(visible(0),'$ 0');assert.equal(visible(-15000),'-$ 15.000');
 const value='9007199254740993.25';assert.equal(visible(value),'$ 9.007.199.254.740.993,25');assert.equal(value,'9007199254740993.25');
 for(const v of [null,undefined,NaN,Infinity,'',{},true])assert.equal(money(v),'No disponible');
});
test('recuperación explícita conserva el intento remoto aunque no exista clave en el navegador', async () => {
  const calls = []; configureSession('sentinel');
  globalThis.fetch = async (url, options) => {
    calls.push([url, options]);
    if (url.endsWith('/compras/7')) return Response.json({ estado: 'PROCESANDO' });
    if (url.includes('/pagos?')) return Response.json([{ id: 9, compraId: 7, estado: 'RESULTADO_DESCONOCIDO', claveIdempotencia: 'original', medioPago: 'TARJETA_CREDITO' }]);
    return Response.json({ id: 9, estado: 'APROBADO' });
  };
  assert.equal((await recoverPayment(7)).id, 9);
  assert.equal(calls.length, 3);
  assert.deepEqual(JSON.parse(calls[2][1].body), { compraId: 7, claveIdempotencia: 'original', medioPago: 'TARJETA_CREDITO' });
  assert.equal(calls.filter(c => c[1].method === 'POST').length, 1);
});
for (const attempts of [[], [{ id: 1, compraId: 99, claveIdempotencia: 'other', estado: 'APROBADO' }], [{ id: 1, compraId: 7, estado: 'PENDIENTE' }], [{ id: 1 }, { id: 2 }]]) test('recuperación sin intento original inequívoco no despacha escrituras: ' + JSON.stringify(attempts), async () => {
  let writes = 0;
  globalThis.fetch = async (url, options) => { if (options.method !== 'GET') writes++; return Response.json(url.includes('/pagos?') ? attempts : { estado: 'PROCESANDO' }); };
  await assert.rejects(recoverPayment(7), e => e.status === 409); assert.equal(writes, 0);
});
test('recuperación con respuesta perdida no repite el POST', async () => {
  let writes = 0;
  globalThis.fetch = async (url, options) => {
    if (options.method === 'POST') { writes++; throw Error('lost'); }
    return Response.json(url.includes('/pagos?') ? [{ id: 1, compraId: 7, estado: 'APROBADO', claveIdempotencia: 'original', medioPago: null }] : { estado: 'PROCESANDO' });
  };
  await assert.rejects(recoverPayment(7), e => e.status === 0); assert.equal(writes, 1);
});
for (const estado of ['PENDIENTE', 'RESULTADO_DESCONOCIDO', 'APROBADO', 'RECHAZADO', 'REINTEGRO_DESCONOCIDO', 'REINTEGRADO_TOTAL']) test(`recuperación ${estado} conserva exactamente el intento y medio originales`, async () => {
  const calls = []; configureSession('sentinel');
  const medioPago = estado === 'REINTEGRADO_TOTAL' ? null : 'TARJETA_CREDITO';
  const etapa = { PENDIENTE: 'PAGO', RESULTADO_DESCONOCIDO: 'PAGO', APROBADO: 'EMISION', RECHAZADO: 'LIBERACION', REINTEGRO_DESCONOCIDO: 'COMPENSACION', REINTEGRADO_TOTAL: 'LIBERACION' }[estado];
  const attempt = { id: 9, compraId: 7, estado, claveIdempotencia: 'original-' + estado, medioPago };
  globalThis.fetch = async (url, options) => {
    calls.push([url, options]);
    if (url.endsWith('/compras/7')) return Response.json({ estado: 'PROCESANDO', etapa });
    if (url.endsWith('/pagos?compraId=7')) return Response.json([attempt]);
    assert.equal(url, '/gateway/pagos'); assert.equal(options.method, 'POST');
    return Response.json(attempt);
  };
  assert.equal((await recoverPayment(7)).id, attempt.id);
  assert.deepEqual(calls.map(c => [c[0], c[1].method]), [['/gateway/compras/7', 'GET'], ['/gateway/pagos?compraId=7', 'GET'], ['/gateway/pagos', 'POST']]);
  assert.deepEqual(JSON.parse(calls[2][1].body), { compraId: 7, claveIdempotencia: attempt.claveIdempotencia, medioPago });
  assert.ok(calls.every(c => c[1].headers.Authorization === 'Bearer sentinel'));
});
for (const estado of ['RECHAZADO', 'REINTEGRO_DESCONOCIDO', 'REINTEGRADO_TOTAL']) test(`recuperación ${estado} no reintenta una respuesta técnica 503`, async () => {
  let posts = 0;
  globalThis.fetch = async (url, options) => {
    if (options.method === 'POST') { posts++; return new Response(null, { status: 503 }); }
    return Response.json(url.includes('/pagos?') ? [{ id: 9, compraId: 7, estado, claveIdempotencia: 'original', medioPago: 'TARJETA_DEBITO' }] : { estado: 'PROCESANDO' });
  };
  await assert.rejects(recoverPayment(7), e => e.status === 503); assert.equal(posts, 1);
});
for (const estado of ['PENDIENTE', 'COMPLETADA', 'FALLIDA', 'CANCELADA', 'DEVUELTA_PARCIAL', 'DEVUELTA_TOTAL', 'DESCONOCIDA']) test(`recuperación de compra ${estado} bloqueada antes de consultar o procesar pagos`, async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => { calls.push([url, options.method]); return Response.json({ estado }); };
  await assert.rejects(recoverPayment(7), e => e.status === 409);
  assert.deepEqual(calls, [['/gateway/compras/7', 'GET']]);
});
test('recuperación de compra PROCESANDO con etapa FINALIZADA no despacha escrituras', async () => {
  let calls = 0;
  globalThis.fetch = async () => { calls++; return Response.json({ estado: 'PROCESANDO', etapa: 'FINALIZADA' }); };
  await assert.rejects(recoverPayment(7), e => e.status === 409); assert.equal(calls, 1);
});
for (const estado of ['REINTEGRADO_PARCIAL', 'DESCONOCIDO', null, undefined]) test(`recuperación de pago incompatible ${estado} no despacha escrituras`, async () => {
  let writes = 0;
  globalThis.fetch = async (url, options) => {
    if (options.method !== 'GET') writes++;
    return Response.json(url.includes('/pagos?') ? [{ id: 9, compraId: 7, estado, claveIdempotencia: 'original', medioPago: 'TARJETA_DEBITO' }] : { estado: 'PROCESANDO' });
  };
  await assert.rejects(recoverPayment(7), e => e.status === 409); assert.equal(writes, 0);
});
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
