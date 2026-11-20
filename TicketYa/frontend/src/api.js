// Tokens are kept in memory only. All browser traffic goes through the Gateway proxy.
let token = null;
let expired = () => {};
export function configureSession(value, onExpired = () => {}) { token = value; expired = onExpired; }
export class ApiError extends Error {
  constructor(status, correlation) {
    const messages = { 400: 'Revisá los datos ingresados.', 401: 'La sesión venció. Volvé a ingresar.', 403: 'Tu cuenta no tiene permiso para esta acción.', 404: 'El recurso no existe o no está disponible para tu cuenta.', 409: 'El estado cambió o la operación está en curso. Consultá el estado actual.', 422: 'La operación no cumple las condiciones del evento.', 429: 'El servicio está ocupado. Esperá antes de intentar otra operación.', 503: 'El servicio no está disponible temporalmente.' };
    super(messages[status] || 'No se pudo confirmar el resultado. Consultá el estado antes de repetir una operación.');
    this.status = status; this.correlation = correlation;
  }
}
export async function api(path, { method = 'GET', body, signal, anonymous = false } = {}) {
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('://')) throw new Error('Ruta no permitida');
  let response;
  try {
    response = await fetch('/gateway' + path, { method, signal, credentials: 'omit', cache: 'no-store', headers: { Accept: 'application/json', ...(!anonymous && token ? { Authorization: 'Bearer ' + token } : {}), ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) }, ...(body !== undefined ? { body: JSON.stringify(body) } : {}) });
  } catch (e) { if (e.name === 'AbortError') throw e; throw new ApiError(0); }
  if (!response.ok) { if (response.status === 401 && token && !anonymous) expired(); const error = new ApiError(response.status, response.headers.get('X-Correlation-Id')); if (anonymous && response.status === 401) error.message = 'No se pudo ingresar. Revisá correo y contraseña.'; throw error; }
  if (response.status === 204) return null;
  const text = await response.text();
  try { return text ? JSON.parse(text) : null; } catch { throw new ApiError(0); }
}
export const get = path => api(path);
export const send = (path, method, body) => api(path, { method, body });
// Explicit recovery only: use the server's original attempt, never a new browser key.
export async function recoverPayment(purchase) {
  const current = await get('/compras/' + purchase);
  if (current.estado !== 'PROCESANDO') throw new ApiError(409);
  const attempts = await get('/pagos?compraId=' + purchase);
  if (!Array.isArray(attempts) || attempts.length !== 1) throw new ApiError(409);
  const original = attempts[0];
  if (String(original.compraId) !== String(purchase) || !original.id ||
      typeof original.claveIdempotencia !== 'string' || !original.claveIdempotencia.trim() ||
      original.claveIdempotencia.length > 100 ||
      !['PENDIENTE', 'RESULTADO_DESCONOCIDO', 'APROBADO'].includes(original.estado)) throw new ApiError(409);
  return send('/pagos', 'POST', { compraId: Number(purchase), claveIdempotencia: original.claveIdempotencia, medioPago: original.medioPago ?? null });
}
export const hasRole = (user, role) => Boolean(user?.roles?.includes(role));
export const money = value => value == null ? 'No disponible' : new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 }).format(value) + ' u.m.';
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
