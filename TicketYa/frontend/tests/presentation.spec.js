// UI-only fixtures. Every Gateway request is intercepted; no real business writes.
import {test,expect} from '@playwright/test';
const reference='77f61506-0b95-49f2-aea8-205fd5c17592';
const event={eventId:1,name:'Evento de prueba UI',eventType:'Recital',status:'PUBLICADO',venueId:1,organizerId:1,minPrice:28000,startDateTime:'2030-12-01T21:00:00Z',sectorPrices:[{sectorId:1,price:28000}]};
async function setup(page,role='CLIENTE'){
 const calls=[];let reserve=0,preferences=0;
 page.on('pageerror',error=>{throw error;});
 await page.route('**/gateway/**',async route=>{
  const req=route.request(),p=new URL(req.url()).pathname.replace('/gateway',''),method=req.method();calls.push({p,method,body:req.postDataJSON()});
  let data={},status=200;
  if(p==='/auth/login')data={access_token:'presentation-fixture-only',token_type:'Bearer',expires_in:3600};
  else if(p==='/auth/me')data={id:1,email:'ui@example.test',roles:[role]};
  else if(p==='/api/v1/events/search')data=[event];
  else if(p==='/api/v1/events/1')data=event;
  else if(p==='/api/v1/venues/1')data={name:'Lugar UI',city:'Tandil',address:'Dirección de prueba'};
  else if(p==='/api/v1/venues/1/sectors')data=[{sectorId:1,name:'Campo'}];
  else if(p.startsWith('/disponibilidad/'))data={disponibles:100,capacidadTotal:100,esNumerado:false};
  else if(p==='/reservas'){reserve++;status=reserve===1?400:reserve===2?409:201;data=reserve===1?{mensaje:'La reserva supera el limite de entradas por cliente y evento',exitoso:false}:reserve===2?{mensaje:'Capacidad o aforo insuficiente',exitoso:false}:{idReserva:1,estado:'PENDIENTE',cantidad:2,fechaExpiracion:'2030-12-01T20:00:00'};}
  else if(p==='/compras'&&method==='POST'){status=400;data={message:'La reserva no es valida para el cliente y evento indicados',status:400};}
  else if(p==='/api/v1/participants')data=[];
  else if(p==='/api/v1/me/preferences'){if(method==='PUT'){preferences++;if(preferences===2){status=500;data={message:'SQL password=secret-sentinel'};}}else data={favoriteTypes:[],favoriteParticipantIds:[],preferredCity:''};}
  else if(p==='/api/v1/events/1/report')data={amountUnit:'u.m.',totals:{grossCollected:28000,refundedAmount:1000.25,netCollected:null,soldTickets:2}};
  else {status=404;data={message:'Not found'};}
  await route.fulfill({status,json:data,headers:{'X-Correlation-Id':reference}});
 });
 await page.goto('/#/login');await page.getByLabel('Correo electrónico').fill('ui@example.test');await page.getByLabel('Contraseña',{exact:true}).fill('fixture-only-password');await page.getByRole('button',{name:'Ingresar',exact:true}).click();await expect(page.getByRole('button',{name:'Salir'})).toBeVisible();return calls;
}
test('catálogo, sector y reserva: ARS, cupo, stock y referencia secundaria',async({page})=>{
 const calls=await setup(page);await expect(page.getByText('Desde $ 28.000',{exact:true})).toBeVisible();await page.locator('a.event-card').click();await page.getByRole('combobox',{name:/Sector/}).selectOption('1');await expect(page.getByRole('option',{name:'Campo · $ 28.000'})).toHaveCount(1);await page.getByRole('button',{name:'Consultar disponibilidad'}).click();await page.getByLabel('Cantidad',{exact:true}).fill('7');await page.getByRole('button',{name:'Reservar entradas',exact:true}).click();await expect(page.getByRole('alert')).toContainText('límite de 6 entradas');await expect(page.locator('.error-reference')).not.toHaveAttribute('open','');await expect(page.locator('.error-reference code')).not.toBeVisible();await page.getByText('Referencia para soporte',{exact:true}).click();await expect(page.locator('.error-reference code')).toHaveText(reference);
 await page.getByLabel('Cantidad',{exact:true}).fill('2');await page.getByRole('button',{name:'Reservar entradas',exact:true}).click();await expect(page.getByRole('alert')).toContainText('No quedan entradas suficientes');await page.getByRole('button',{name:'Reservar entradas',exact:true}).click();await expect(page.getByRole('status')).toContainText('Reserva 1');await page.getByRole('button',{name:'Continuar con la compra'}).click();await expect(page.getByRole('alert')).toContainText('puede haber vencido');await expect(page.getByText(/Reserva 1 · PENDIENTE/)).toHaveCount(0);expect(calls.filter(c=>c.p==='/reservas'&&c.method==='POST')).toHaveLength(3);expect(calls.filter(c=>c.p==='/compras'&&c.method==='POST')).toHaveLength(1);expect(calls.some(c=>c.p==='/pagos')).toBe(false);
});
test('preferencias: un error nuevo elimina el éxito previo y no expone detalles internos',async({page})=>{
 await setup(page);await page.getByRole('link',{name:'Preferencias',exact:true}).click();await page.getByRole('button',{name:'Guardar preferencias'}).click();await expect(page.getByText('Preferencias guardadas.',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Guardar preferencias'}).click();await expect(page.getByRole('alert')).toBeVisible();await expect(page.getByText('Preferencias guardadas.',{exact:true})).toHaveCount(0);await expect(page.locator('body')).not.toContainText('secret-sentinel');
});
test('reportes: solo montos en ARS, cantidades intactas y neto desconocido sin inventar cero',async({page})=>{
 await setup(page,'ORGANIZADOR');await page.evaluate(()=>{location.hash='/report/1';});await expect(page.getByText('ARS (presentación, sin conversión)',{exact:true})).toBeVisible();await expect(page.getByText('$ 28.000',{exact:true})).toBeVisible();await expect(page.getByText('$ 1.000,25',{exact:true})).toBeVisible();await expect(page.getByText('No disponible',{exact:true})).toBeVisible();await expect(page.getByText('2',{exact:true})).toBeVisible();await expect(page.locator('main')).not.toContainText('u.m.');
});
test('checkout conserva importe, medio y un único POST; muestra el resultado confirmado por la respuesta',async({page})=>{
 await setup(page);let state='PENDIENTE';const posts=[];
 await page.route('**/gateway/compras/9',route=>route.fulfill({json:{id:9,estado:state,etapa:state==='PENDIENTE'?'PAGO':'FINALIZADA',importeTotal:56000}}));
 await page.route('**/gateway/pagos',async route=>{posts.push(route.request().postDataJSON());state='COMPLETADA';await route.fulfill({status:201,json:{id:9,estado:'APROBADO',importe:56000}});});
 await page.route('**/gateway/tickets**',route=>route.fulfill({json:[{ticketId:'ui-ticket-1',eventId:1,sectorId:1,status:'EMITIDA',qr:'ui-only-qr-fixture'}]}));
 await page.evaluate(()=>{location.hash='/purchase/9';});await expect(page.getByText('$ 56.000',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Pagar con débito',exact:true}).click();await expect(page.getByText('Pago #9: APROBADO',{exact:true})).toBeVisible();expect(posts).toHaveLength(1);expect(posts[0].compraId).toBe(9);expect(posts[0].medioPago).toBe('TARJETA_DEBITO');expect(posts[0].importe).toBeUndefined();expect(posts[0].claveIdempotencia).toBeTruthy();await page.getByRole('link',{name:'Ver mis entradas',exact:true}).click();await expect(page.getByTestId('qr-token')).toHaveText('ui-only-qr-fixture');expect(posts).toHaveLength(1);
});
