import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import { fixture } from './isolated-fixture.js';
let f;
test.describe.configure({ mode: 'serial' });
test.beforeAll(async () => { test.setTimeout(240000); f = await fixture(); });
test.afterAll(async () => { if (f) f.verify(); });
test.beforeEach(async ({ page }) => { page.on('pageerror',error=>console.error('Frontend runtime:',error.message)); page.setDefaultTimeout(12000); });
async function login(page, user) {
  await page.goto('/#/login'); await page.getByLabel('Correo electrónico').fill(user.email);
  try { await page.getByLabel('Contraseña', { exact: true }).fill(user.password); } catch { throw Error('No se pudo completar el campo privado'); }
  await page.getByRole('button',{name:'Ingresar',exact:true}).click(); await expect(page.getByRole('button',{name:'Salir',exact:true})).toBeVisible();
}
test('visitante: catálogo, filtro real, detalle público y responsive', async ({ page }) => {
  await page.goto('/'); await expect(page.getByRole('heading',{name:'Hay encuentros que no te podés perder.'})).toBeVisible();
  await page.getByLabel('Ciudad',{exact:true}).fill('Tandil'); await page.getByRole('button',{name:'Buscar eventos'}).click();
  await page.getByRole('link').filter({hasText:f.event.name}).click(); await expect(page.getByRole('heading',{name:f.event.name})).toBeVisible();
  await expect(page.getByRole('link',{name:'Ingresar para reservar'})).toBeVisible();
  await page.setViewportSize({width:390,height:844}); await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await page.screenshot({path:'test-results/catalogue-mobile.png',fullPage:true});
});
test('cliente: sesión JWT, reserva, compra, pago y entradas reales', async ({ page }) => {
  await login(page,f.client); await page.getByRole('link').filter({hasText:f.event.name}).click();
  await page.getByRole('combobox',{name:/Sector/}).selectOption(String(f.sector.sectorId));
  await page.getByRole('button',{name:'Consultar disponibilidad'}).click(); await expect(page.getByText('2 de 2 lugares disponibles ahora.')).toBeVisible();
  await page.getByRole('button',{name:'Reservar entradas',exact:true}).click(); await expect(page.getByText(/Reserva \d+ · PENDIENTE/)).toBeVisible();
  await page.getByRole('button',{name:'Continuar con la compra'}).click(); await expect(page.getByRole('heading',{name:/Tu compra #/})).toBeVisible();
  const purchaseId=Number(page.url().split('/').at(-1)); await page.getByRole('button',{name:'Pagar con débito'}).click();
  await expect(page.getByText('COMPLETADA',{exact:true})).toBeVisible({timeout:30000});
  await page.getByRole('link',{name:'Ver mis entradas',exact:true}).click(); await expect(page.getByTestId('qr-token')).toHaveCount(1);
  const tickets=await f.call('/tickets?clientId='+f.client.id,'GET',undefined,f.client.token); expect(tickets.length).toBe(1); expect(tickets[0].status).toBe('EMITIDA');
  const payments=await f.call('/pagos?compraId='+purchaseId,'GET',undefined,f.client.token); expect(payments.length).toBe(1); expect(payments[0].estado).toBe('APROBADO');
  expect((await f.call('/compras/'+purchaseId,'GET',undefined,f.client.token)).importeTotal).toBe(15000);
  fs.writeFileSync('test-results/financial-evidence.json',JSON.stringify({project:f.project,event:f.event.eventId,purchase:purchaseId,payments:1,tickets:1,total:15000,noDuplicatePayments:true}));
  await page.screenshot({path:'test-results/client-tickets.png',fullPage:true});
});
test('cliente: preferencias MongoDB, recomendaciones y SSE con bandeja REST', async ({ page }) => {
  await login(page,f.client); await page.getByRole('link',{name:'Preferencias',exact:true}).click(); await page.getByLabel('Ciudad preferida').fill('Tandil');
  await page.getByLabel('Recital',{exact:true}).check(); await page.getByRole('button',{name:'Guardar preferencias'}).click(); await expect(page.getByText('Preferencias guardadas.')).toBeVisible();
  await page.getByRole('link',{name:'Para vos',exact:true}).click(); await expect(page.getByRole('heading',{name:'Para vos',exact:true})).toBeVisible(); await expect(page.getByRole('status')).toHaveCount(0);
  await page.getByRole('link',{name:'Notificaciones',exact:true}).click(); await expect(page.getByText('● Conectado',{exact:true})).toBeVisible({timeout:15000});
  const unread=page.getByRole('button',{name:'Marcar como leída'}); await expect(unread.first()).toBeVisible(); await unread.first().click(); await expect(page.getByText('Leída',{exact:true}).first()).toBeVisible();
  await expect(page.getByRole('link',{name:'Mis eventos',exact:true})).toHaveCount(0); await page.evaluate(()=>{location.hash='/access';}); await expect(page.getByText('Tu cuenta no tiene acceso a esta pantalla.')).toBeVisible();
  await page.getByRole('button',{name:'Salir',exact:true}).click(); await page.goto('/#/tickets'); await expect(page.getByRole('link',{name:'Ingresá para continuar'})).toBeVisible();
});
test('organizador: eventos propios y reporte, sin permisos de cliente', async ({ page }) => {
  await login(page,f.organizer); await page.getByRole('link',{name:'Mis eventos',exact:true}).click();
  await page.getByRole('link').filter({hasText:f.event.name}).click(); await page.getByRole('link',{name:'Ver reporte comercial'}).click();
  await expect(page.getByRole('heading',{name:'Reporte del evento #'+f.event.eventId})).toBeVisible(); await expect(page.getByText('UNIDAD_MONETARIA_DEL_SISTEMA',{exact:true})).toBeVisible();
  await expect(page.getByRole('link',{name:'Entradas',exact:true})).toHaveCount(0);
});
test('acceso y errores: exige asignación real al evento y rechaza login inválido', async ({ page }) => {
  await login(page,f.access); await page.getByRole('link',{name:'Control de acceso'}).click();
  await page.getByLabel('ID de evento',{exact:true}).fill(String(f.event.eventId)); await page.getByLabel('Token QR',{exact:true}).fill('00000000-0000-4000-8000-000000000000'); await page.getByRole('button',{name:'Validar entrada'}).click();
  await expect(page.getByRole('alert')).toContainText('Tu cuenta no tiene permiso');
  await page.getByRole('button',{name:'Salir',exact:true}).click(); await page.goto('/#/login'); await page.getByLabel('Correo electrónico').fill(f.client.email); await page.getByLabel('Contraseña',{exact:true}).fill('incorrect-password'); await page.getByRole('button',{name:'Ingresar',exact:true}).click(); await expect(page.getByRole('alert')).toBeVisible();
});
test('numerados: selecciona dos asientos reales y reserva exactamente esa capacidad', async ({page})=>{
  const venue=await f.call('/api/v1/venues','POST',{name:'Numerados web '+f.event.eventId,address:'Fixture aislada',city:'Tandil',latitude:-37.32,longitude:-59.13},f.organizer.token);
  const sector=await f.call(`/api/v1/venues/${venue.venueId}/sectors`,'POST',{name:'Numerado web',type:'NUMERADO',capacity:2,rows:1,seatsPerRow:2},f.organizer.token);
  const event=await f.call('/api/v1/events','POST',{name:'Asientos web '+f.event.eventId,eventType:'Teatro',organizerId:f.organizer.id,venueId:venue.venueId,startDateTime:new Date(Date.now()+8*86400000).toISOString()},f.organizer.token);
  await f.call(`/api/v1/events/${event.eventId}/prices`,'POST',[{sectorId:sector.sectorId,price:15000}],f.organizer.token);await f.call(`/api/v1/events/${event.eventId}/publish`,'PATCH',undefined,f.organizer.token);
  await login(page,f.client);await page.getByRole('link').filter({hasText:event.name}).click();await page.getByRole('combobox',{name:/Sector/}).selectOption(String(sector.sectorId));await page.getByRole('button',{name:'Consultar disponibilidad'}).click();
  const seats=page.getByRole('group',{name:'Seleccioná tus asientos'}).getByRole('checkbox');await expect(seats).toHaveCount(2);await seats.nth(0).check();await seats.nth(1).check();
  const response=page.waitForResponse(r=>r.url().endsWith('/gateway/reservas')&&r.request().method()==='POST');await page.getByRole('button',{name:'Reservar entradas',exact:true}).click();const r=await response;expect(r.status()).toBe(201);const reservation=await r.json();
  const submitted=r.request().postDataJSON();expect(submitted.idsAsientos.length).toBe(2);expect(new Set(submitted.idsAsientos).size).toBe(2);
  expect((await f.call('/reservas/'+reservation.idReserva,'GET',undefined,f.client.token)).cantidad).toBe(2);
  expect((await f.call(`/disponibilidad/eventos/${event.eventId}/sectores/${sector.sectorId}`,'GET',undefined,f.client.token)).disponibles).toBe(0);
});
