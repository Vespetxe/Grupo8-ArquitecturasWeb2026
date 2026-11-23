// Tokens are kept in memory only. All browser traffic goes through the Gateway proxy.
let token = null;
let expired = () => {};
export function configureSession(value, onExpired = () => {}) { token = value; expired = onExpired; }
export class ApiError extends Error {
  constructor(status, correlation, business) {
    const messages = { 400: 'Revisá los datos ingresados.', 401: 'La sesión venció. Volvé a ingresar.', 403: 'Tu cuenta no tiene permiso para esta acción.', 404: 'El recurso no existe o no está disponible para tu cuenta.', 409: 'El estado cambió o la operación está en curso. Consultá el estado actual.', 422: 'La operación no cumple las condiciones del evento.', 429: 'El servicio está ocupado. Esperá antes de intentar otra operación.', 503: 'El servicio no está disponible temporalmente.' };
    super(business?.message || messages[status] || 'No se pudo confirmar el resultado. Consultá el estado antes de repetir una operación.');
    this.status = status; this.reason = business?.reason;
    this.correlation = typeof correlation === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(correlation) ? correlation : undefined;
  }
}
// Exact, controlled translations only. Never display arbitrary remote messages.
function businessError(status, path, method, body) {
  if (!body || typeof body !== 'object' || Array.isArray(body) || body.exitoso === true ||
      (body.status != null && body.status !== status) || method !== 'POST') return;
  const message = typeof body.mensaje === 'string' ? body.mensaje : body.message;
  const reserva = path === '/reservas', compra = path === '/compras', pago = path === '/pagos';
  if (status === 400 && ((reserva && message === 'La reserva supera el limite de entradas por cliente y evento') ||
      (compra && message === 'La compra supera el limite de entradas permitido por cliente y evento')))
    return { reason: 'ENTRY_LIMIT', message: 'No podés superar el límite de 6 entradas para este evento, contando tus reservas vigentes y entradas adquiridas.' };
  if (reserva && status === 409 && message === 'Capacidad o aforo insuficiente')
    return { reason: 'INSUFFICIENT_STOCK', message: 'No quedan entradas suficientes para la cantidad elegida. Consultá nuevamente la disponibilidad.' };
  if (reserva && status === 409 && message === 'Uno o más asientos seleccionados ya no se encuentran disponibles')
    return { reason: 'SEATS_UNAVAILABLE', message: 'Uno o más asientos ya no están disponibles. Consultá la disponibilidad y elegí otros.' };
  if ((compra || pago) && [400, 409].includes(status) && message === 'La reserva se encuentra vencida')
    return { reason: 'RESERVATION_EXPIRED', message: 'La reserva venció. Volvé al catálogo e iniciá una nueva reserva.' };
  if (status === 400 && ((compra && message === 'La reserva no es valida para el cliente y evento indicados') ||
      (pago && message === 'La reserva no es valida, esta vencida o no corresponde a la compra')))
    return { reason: 'RESERVATION_UNAVAILABLE', message: 'La reserva no es válida para esta operación: puede haber vencido o no corresponder a la compra. Consultá su estado antes de continuar.' };
  if (reserva && status === 409 && message === 'Debe ingresar a la cola')
    return { reason: 'QUEUE_REQUIRED', message: 'Este evento requiere fila virtual. Ingresá a la fila antes de reservar.' };
  if (reserva && status === 409 && message === 'Turno no habilitado o vencido; consulte su estado')
    return { reason: 'QUEUE_NOT_READY', message: 'Tu turno todavía no está habilitado o venció. Consultá su estado en la fila virtual.' };
}
export const userErrorMessage = error => error instanceof ApiError ? error.message : 'No se pudo completar la operación. Consultá su estado antes de volver a intentarlo.';
export const errorReference = error => error instanceof ApiError ? error.correlation : undefined;
export async function api(path, { method = 'GET', body, signal, anonymous = false } = {}) {
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('://')) throw new Error('Ruta no permitida');
  let response;
  try {
    response = await fetch('/gateway' + path, { method, signal, credentials: 'omit', cache: 'no-store', headers: { Accept: 'application/json', ...(!anonymous && token ? { Authorization: 'Bearer ' + token } : {}), ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) }, ...(body !== undefined ? { body: JSON.stringify(body) } : {}) });
  } catch (e) { if (e.name === 'AbortError') throw e; throw new ApiError(0); }
  if (!response.ok) {
    if (response.status === 401 && token && !anonymous) expired();
    let business;
    if ([400, 409].includes(response.status) && response.headers.get('Content-Type')?.includes('application/json')) {
      try { const text = await response.text(); if (text.length <= 4096) business = businessError(response.status, path, method, JSON.parse(text)); } catch { /* Preserve HTTP failure, never retry. */ }
    }
    const error = new ApiError(response.status, response.headers.get('X-Correlation-Id'), business);
    if (anonymous && response.status === 401) error.message = 'No se pudo ingresar. Revisá correo y contraseña.';
    throw error;
  }
  if (response.status === 204) return null;
  const text = await response.text();
  try { return text ? JSON.parse(text) : null; } catch { throw new ApiError(0); }
}
export const get = path => api(path);
export const send = (path, method, body) => api(path, { method, body });
// Explicit recovery only: use the server's original attempt, never a new browser key.
export async function recoverPayment(purchase) {
  const current = await get('/compras/' + purchase);
  if (current.estado !== 'PROCESANDO' || current.etapa === 'FINALIZADA') throw new ApiError(409);
  const attempts = await get('/pagos?compraId=' + purchase);
  if (!Array.isArray(attempts) || attempts.length !== 1) throw new ApiError(409);
  const original = attempts[0];
  if (String(original.compraId) !== String(purchase) || !original.id ||
      typeof original.claveIdempotencia !== 'string' || !original.claveIdempotencia.trim() ||
      original.claveIdempotencia.length > 100 ||
      !['PENDIENTE', 'RESULTADO_DESCONOCIDO', 'APROBADO', 'RECHAZADO', 'REINTEGRO_DESCONOCIDO', 'REINTEGRADO_TOTAL'].includes(original.estado)) throw new ApiError(409);
  return send('/pagos', 'POST', { compraId: Number(purchase), claveIdempotencia: original.claveIdempotencia, medioPago: original.medioPago ?? null });
}
export const hasRole = (user, role) => Boolean(user?.roles?.includes(role));
const ars = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', minimumFractionDigits: 0, maximumFractionDigits: 2 });
export const money = value => (typeof value === 'number' || (typeof value === 'string' && value.trim() !== '')) && Number.isFinite(Number(value)) ? ars.format(value) : 'No disponible';
export const date = value => value ? new Date(value).toLocaleString('es-AR', { dateStyle: 'medium', timeStyle: 'short' }) : 'Sin fecha';
export function paymentKey(owner, purchase, storage = sessionStorage) {
  const name = `ticketya:payment:${owner}:${purchase}`;
  const current = storage.getItem(name);
  if (current) return current;
  const key = crypto.randomUUID(); storage.setItem(name, key); return key;
}
// fetch, rather than EventSource, keeps the bearer token out of URLs.
export async function streamNotifications(signal, onEvent, onState) {
  while (!signal.aborted) {
    try {
      const response = await fetch('/gateway/api/v1/me/notifications/stream', { credentials: 'omit', headers: { Authorization: 'Bearer ' + token, Accept: 'text/event-stream' }, signal, cache: 'no-store' });
      if (response.status === 401) { expired(); return; }
      if (!response.ok || !response.body) throw new Error('Stream unavailable');
      onState('Conectado');
      const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = '';
      try {
        while (true) {
          const { done, value } = await reader.read(); if (done) break;
          buffer += decoder.decode(value, { stream: true });
          buffer = buffer.replace(/\r\n/g, '\n');
          if (buffer.length > 65536) throw new Error('Stream frame limit');
          let end;
          while ((end = buffer.indexOf('\n\n')) >= 0) { const frame = buffer.slice(0, end); buffer = buffer.slice(end + 2); if (frame.split('\n').some(line => line.startsWith('data:'))) onEvent(); }
        }
      } finally { await reader.cancel().catch(() => {}); }
    } catch { if (signal.aborted) return; }
    onState('Reconectando · la bandeja REST sigue disponible');
    await new Promise(resolve => { if (signal.aborted) return resolve(); const finish = () => { clearTimeout(timer); signal.removeEventListener('abort', finish); resolve(); }; const timer = setTimeout(finish, 3000); signal.addEventListener('abort', finish, { once: true }); });
  }
}
