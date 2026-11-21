'use strict';
const fs=require('node:fs'),path=require('node:path');
const d=require('./lib.cjs');
class VerificationFailure extends Error {}
function need(ok,message){if(!ok)throw new VerificationFailure('FAIL: '+message);}
function equal(actual,expected,label){need(actual===expected,label+' no coincide con el escenario esperado');}
function money(value,label){need((typeof value==='number'||typeof value==='string')&&/^\d+(\.\d{1,2})?$/.test(String(value)),label+' ausente o importe inválido');const cents=Math.round(Number(value)*100);need(Number.isSafeInteger(cents),label+' fuera de rango');return cents;}
function instant(value,label){need(typeof value==='string'&&value.includes('T'),label+' ausente o fecha inválida');const ms=Date.parse(/(?:Z|[+-]\d\d:\d\d)$/.test(value)?value:value+'Z');need(Number.isFinite(ms),label+' fecha inválida');return ms;}
function ids(actual,expected,label){need(Array.isArray(actual),label+' debe ser una lista');need(actual.length===expected.length&&new Set(actual).size===actual.length&&actual.every(v=>expected.includes(v)),label+' no coincide con la selección original');}
async function verify(c=d.readConfig()){
 let phase='preflight',accredited=false;
 try{
  need(fs.existsSync(path.join(d.runtime,'seed-complete.json')),'la carga demo no está completa');d.attest(c);accredited=true;
  const j=new d.Journal(path.join(d.runtime,'seed-journal.json')),s=d.session(c),events=[],scenarios=[],stocks=new Map(),seenTickets=new Set(),seenQr=new Set(),seenPayments=new Set(),seenPurchases=new Set(),seenReservations=new Set(),pendingDeadlines=[];
  const call=(actor,url)=>s.call(actor,'GET',url);
  const acquired=new Map(),eventRows=new Map();
  const counts=()=>({soldTickets:0,returnedTickets:0,netSoldTickets:0,grossCollected:0,refundedAmount:0,netCollected:0,issuedTickets:0,activeTickets:0,annulledTickets:0});
  phase='catálogo';
  for(const e of d.catalog.events){
   const v=d.catalog.venues.find(v=>v.key===e.venue),event=j.get('event:'+e.key).eventId,actual=await call(v.organizer,'/api/v1/events/'+event);
   equal(actual.name,e.name,'Nombre del evento');equal(actual.status,'PUBLICADO','Estado del evento');equal(actual.venueId,j.get('venue:'+e.venue).venueId,'Lugar del evento');equal(actual.eventId,event,'ID de evento');need(Array.isArray(actual.sectorPrices),'Precios del evento ausentes');
   const rows=[];for(let i=0;i<v.sectors.length;i++){const sector=j.get('sector:'+v.key+':'+i).sectorId,price=actual.sectorPrices.filter(p=>p.sectorId===sector);equal(price.length,1,'Precio único por sector');equal(money(price[0].price,'Precio del catálogo'),e.prices[i]*100,'Precio del catálogo');const row={event,sector,capacity:v.sectors[i].capacity,type:v.sectors[i].type,metrics:counts(),held:0};stocks.set(event+':'+sector,row);rows.push(row);}
   eventRows.set(e.key,{e,v,event,rows});events.push({name:e.name,eventId:event,city:v.city});
  }
  for(const x of d.catalog.scenarios){
   phase='escenario '+x.key;const prefix='scenario:'+x.key,{e,event}=eventRows.get(x.event),sector=j.get('sector:'+e.venue+':'+x.sector).sectorId,row=stocks.get(event+':'+sector),owner=(await s.user(x.client)).id;
   const originalReservation=j.get(prefix+':reservation'),reservation=await call(x.client,'/reservas/'+originalReservation.idReserva),selection=j.get(prefix+':selection').ids;
   need(!seenReservations.has(reservation.idReserva),'Reserva reutilizada entre escenarios');seenReservations.add(reservation.idReserva);
   equal(reservation.idReserva,originalReservation.idReserva,'ID de reserva');equal(reservation.idCliente,owner,'Cliente de reserva');equal(reservation.idEvento,event,'Evento de reserva');equal(reservation.idSector,sector,'Sector de reserva');equal(reservation.cantidad,x.quantity,'Cantidad reservada');
   need(Array.isArray(reservation.asientosReservados),'Selección de reserva ausente');ids(reservation.asientosReservados.map(a=>a.idAsiento),selection,'Asientos reservados');
   if(x.outcome==='reservation'){
    const expiry=instant(reservation.fechaExpiracion,'Vencimiento de reserva');need(['PENDIENTE','EXPIRADA'].includes(reservation.estado),'Reserva temporal en estado incompatible');
    if(reservation.estado==='PENDIENTE'){need(expiry>Date.now(),'Reserva temporal vencida aún PENDIENTE; esperar al scheduler y volver a consultar');row.held+=x.quantity;pendingDeadlines.push(expiry);}else need(expiry<=Date.now(),'Reserva EXPIRADA antes de su vencimiento');
    scenarios.push({name:x.key,reservation:reservation.idReserva,state:reservation.estado,expiresAt:reservation.fechaExpiracion});continue;
   }
   equal(reservation.estado,x.outcome==='rejected'?'LIBERADA':'CONFIRMADA','Estado de reserva '+x.key);
   const purchase=j.get(prefix+':purchase'),originalPay=j.get(prefix+':payment'),actual=await call(x.client,'/compras/'+purchase.id),payments=await call(x.client,'/pagos?compraId='+purchase.id),tickets=await call(x.client,'/tickets?orderId='+purchase.id);
   equal(actual.id,purchase.id,'ID de compra');equal(actual.clienteId,owner,'Propietario de compra');equal(actual.eventoId,event,'Evento de compra');equal(actual.reservaId,reservation.idReserva,'Reserva asociada a compra');equal(actual.etapa,'FINALIZADA','Etapa final de compra '+x.key);
   equal(actual.estado,x.outcome==='rejected'?'FALLIDA':x.outcome==='partial-refund'?'DEVUELTA_PARCIAL':'COMPLETADA','Estado final de compra '+x.key);
   const total=e.prices[x.sector]*x.quantity*100,refund=x.outcome==='partial-refund'?e.prices[x.sector]*100:0;
   equal(money(actual.importeTotal,'Importe total de compra'),total,'Importe total de compra');need(Array.isArray(actual.detalles)&&actual.detalles.length===(selection.length||1),'Detalle de compra incompleto');
   let quantity=0,subtotal=0;for(const item of actual.detalles){equal(item.sectorId,sector,'Sector del detalle');equal(item.cantidad,selection.length?1:x.quantity,'Cantidad del detalle');equal(money(item.precioUnitario,'Precio histórico'),e.prices[x.sector]*100,'Precio histórico');equal(money(item.subtotal,'Subtotal histórico'),item.cantidad*e.prices[x.sector]*100,'Subtotal histórico');quantity+=item.cantidad;subtotal+=money(item.subtotal,'Subtotal');}
   equal(quantity,x.quantity,'Unidades compradas');equal(subtotal,total,'Suma de subtotales');if(selection.length)ids(actual.detalles.map(a=>a.asientoId),selection,'Asientos comprados');else need(actual.detalles.every(a=>a.asientoId==null),'Asiento en sector general');
   need(Array.isArray(payments)&&payments.length===1,'Se requiere exactamente un pago original');const pay=payments[0];
   equal(pay.id,originalPay.id,'ID de pago original');equal(pay.compraId,purchase.id,'Compra asociada al pago');equal(pay.claveIdempotencia,'demo-'+c.meta.id+'-'+x.key,'Clave del pago original');equal(pay.medioPago,'TARJETA_DEBITO','Medio del pago original');
   equal(pay.estado,x.outcome==='rejected'?'RECHAZADO':x.outcome==='partial-refund'?'REINTEGRADO_PARCIAL':'APROBADO','Estado del pago '+x.key);equal(money(pay.importe,'Importe del pago'),total,'Importe del pago '+x.key);equal(money(pay.importeReintegrado,'Reintegro acumulado del pago'),refund,'Reintegro acumulado del pago');
   need(!seenPayments.has(pay.id)&&!seenPurchases.has(purchase.id),'Pago o compra duplicados entre escenarios');seenPayments.add(pay.id);seenPurchases.add(purchase.id);
   need(Array.isArray(tickets),'Entradas ausentes');equal(tickets.length,x.outcome==='rejected'?0:x.quantity,'Cantidad de entradas');
   for(const t of tickets){need(typeof t.ticketId==='string'&&t.ticketId.length>0&&!seenTickets.has(t.ticketId),'Ticket duplicado o sin ID');seenTickets.add(t.ticketId);need(typeof t.qr==='string'&&t.qr.length>0&&!seenQr.has(t.qr),'QR ausente o duplicado');seenQr.add(t.qr);equal(t.orderId,String(purchase.id),'Orden de la entrada');equal(t.eventId,String(event),'Evento de la entrada');equal(t.clientId,String(owner),'Cliente de la entrada');equal(t.sectorId,String(sector),'Sector de la entrada');if(!selection.length)need(t.seatId==null,'Asiento inesperado en entrada general');}
   if(selection.length)ids(tickets.map(t=>Number(t.seatId)),selection,'Asientos emitidos');
   if(x.outcome==='partial-refund'){
    equal(tickets.filter(t=>t.status==='ANULADA').length,1,'Entradas anuladas por devolución');equal(tickets.filter(t=>t.status==='EMITIDA').length,x.quantity-1,'Entradas restantes vigentes');
    const original=j.get(prefix+':refund'),request=j.get(prefix+':refund-request'),selected=j.get(prefix+':refund-selection').entryId;
    const result=await call('admin','/admin/devoluciones/'+request.id+'/procesamiento');equal(result.etapa,'FINALIZADA','Etapa de devolución');equal(result.cancelacionEvento,false,'Tipo de devolución');
    const dev=result.devolucion,re=result.reintegro;need(dev&&re,'Devolución o reintegro ausentes');equal(dev.id,request.id,'ID de devolución');equal(dev.compraId,purchase.id,'Compra de devolución');equal(dev.clienteId,owner,'Cliente de devolución');equal(dev.eventoId,event,'Evento de devolución');equal(dev.estado,'COMPLETADA','Estado de devolución');equal(money(dev.importeSolicitado,'Importe solicitado'),refund,'Importe solicitado');equal(money(dev.importeReintegrado,'Importe devuelto'),refund,'Importe devuelto');
    need(Array.isArray(dev.detalles)&&dev.detalles.length===1,'Detalle de devolución parcial');equal(dev.detalles[0].entradaId,selected,'Entrada seleccionada para devolución');equal(money(dev.detalles[0].importe,'Importe del detalle devuelto'),refund,'Detalle devuelto');
    equal(re.id,original.reintegro.id,'ID de reintegro original');equal(re.pagoId,pay.id,'Pago del reintegro');equal(re.devolucionId,dev.id,'Devolución del reintegro');equal(re.estado,'COMPLETADO','Estado del reintegro');equal(re.claveIdempotencia,'demo-refund-'+c.meta.id,'Clave del reintegro original');equal(money(re.importe,'Importe del reintegro'),refund,'Importe del reintegro');
    const units=await call(x.client,'/compras/'+purchase.id+'/entradas');need(Array.isArray(units),'Equivalencias de entradas ausentes');const chosen=units.filter(u=>u.entradaId===selected);equal(chosen.length,1,'Equivalencia de entrada devuelta');need(tickets.some(t=>t.ticketId===chosen[0].ticketId&&t.status==='ANULADA'),'La entrada anulada no corresponde a la devolución');
   }else if(x.outcome!=='rejected')need(tickets.every(t=>t.status==='EMITIDA'),'Estado de entradas aprobadas');
   if(x.outcome!=='rejected'){const metrics=row.metrics;metrics.soldTickets+=x.quantity;metrics.returnedTickets+=x.outcome==='partial-refund'?1:0;metrics.grossCollected+=total;metrics.refundedAmount+=refund;metrics.issuedTickets+=x.quantity;metrics.annulledTickets+=x.outcome==='partial-refund'?1:0;const bought=acquired.get(x.client)||new Set();bought.add(event);acquired.set(x.client,bought);}
   scenarios.push({name:x.key,purchase:purchase.id,payment:pay.id,state:actual.estado,tickets:tickets.length});
  }
  phase='conciliación de inventario';
  for(const row of stocks.values()){
   const m=row.metrics;m.netSoldTickets=m.soldTickets-m.returnedTickets;m.netCollected=m.grossCollected-m.refundedAmount;m.activeTickets=m.issuedTickets-m.annulledTickets;
   row.expectedAvailable=row.capacity-m.netSoldTickets-row.held;const stock=await call(null,`/disponibilidad/eventos/${row.event}/sectores/${row.sector}`);equal(stock.idEvento,row.event,'Evento de inventario');equal(stock.idSector,row.sector,'Sector de inventario');equal(stock.esNumerado,row.type==='NUMERADO','Tipo de inventario');equal(stock.capacidadTotal,row.capacity,'Capacidad de inventario');equal(stock.disponibles,row.expectedAvailable,'Stock disponible evento '+row.event+' sector '+row.sector);need(Number.isInteger(stock.disponibles)&&stock.disponibles>=0&&stock.disponibles<=row.capacity,'Inventario fuera de capacidad');
   if(stock.esNumerado){need(Array.isArray(stock.asientosLibres),'Asientos libres ausentes');equal(stock.asientosLibres.length,stock.disponibles,'Asientos libres vs stock');need(new Set(stock.asientosLibres.map(a=>a.idAsiento)).size===stock.asientosLibres.length,'Asientos libres duplicados');}
  }
  // Preferences are compared to seed.cjs's intended values, not just its saved responses.
  phase='preferencias Mongo y recomendaciones';
  const expectedPreferences={ana:{types:['recital'],artist:'bruma',city:'tandil'},bruno:{types:['recital','deporte'],artist:'ensamble',city:'buenos aires'},carla:{types:['festival'],artist:'cocina',city:'rosario'}};
  for(const u of c.users.filter(u=>u.role==='CLIENTE')){
   const expected=expectedPreferences[u.key];need(expected,'Cliente sin preferencias esperadas');const p=await call(u.key,'/api/v1/me/preferences');need(p&&typeof p==='object','Preferencias ausentes');ids(p.favoriteTypes,expected.types,'Tipos favoritos de '+u.key);ids(p.favoriteParticipantIds,[j.get('participant:'+expected.artist).participantId],'Participantes favoritos de '+u.key);equal(p.preferredCity,expected.city,'Ciudad preferida de '+u.key);instant(p.updatedAt,'Actualización de preferencias');
   const rec=await call(u.key,'/api/v1/me/recommendations');equal(rec.historyAvailable,true,'Disponibilidad del historial');need(Array.isArray(rec.events)&&rec.events.length>0&&rec.events.length<=10,'Recomendaciones vacías o fuera de límite para el catálogo demo');need(new Set(rec.events.map(e=>e.id)).size===rec.events.length,'Recomendaciones duplicadas');for(const candidate of rec.events){need(events.some(e=>e.eventId===candidate.id),'Recomendación ajena al catálogo demo');need(!(acquired.get(u.key)||new Set()).has(candidate.id),'Recomendación de un evento ya adquirido');need(instant(candidate.date,'Fecha recomendada')>Date.now(),'Recomendación de evento pasado');}
   need(Array.isArray(await call(u.key,'/api/v1/me/notifications')),'Bandeja de notificaciones inválida');
  }
  function metrics(actual,expected,label,capacity,available){
   need(actual&&typeof actual==='object',label+' métricas ausentes');for(const field of ['soldTickets','returnedTickets','netSoldTickets','issuedTickets','activeTickets','annulledTickets'])equal(actual[field],expected[field],label+' '+field);
   for(const field of ['grossCollected','refundedAmount','netCollected'])equal(money(actual[field],label+' '+field),expected[field],label+' '+field);
   equal(actual.capacity,capacity,label+' capacidad');equal(actual.available,available,label+' disponibilidad');equal(money(actual.occupancyPercent,label+' ocupación'),Math.round(expected.netSoldTickets*10000/capacity),label+' ocupación');need(Number.isInteger(actual.admissions)&&actual.admissions>=0&&actual.admissions<=actual.issuedTickets,label+' admisiones fuera de rango');
  }
  phase='reportes de organizadores';
  for(const key of ['risas-plata','cuerdas-camara','sabores-ribera']){
   const {e,v,event,rows}=eventRows.get(key),report=await call(v.organizer,`/api/v1/events/${event}/report`);need(report&&typeof report==='object','Reporte vacío');equal(report.eventId,event,'Evento del reporte');equal(report.eventName,e.name,'Nombre del reporte');equal(report.eventStatus,'PUBLICADO','Estado del evento reportado');equal(report.financialComplete,true,'Reporte financiero incompleto');equal(report.sourcesAligned,true,'Fuentes del reporte desalineadas');equal(report.amountUnit,'UNIDAD_MONETARIA_DEL_SISTEMA','Unidad del reporte');equal(report.period,'ACUMULADO_DEL_EVENTO','Período del reporte');instant(report.generatedAt,'Fecha del reporte');for(const source of ['compras','entradas','inventario'])instant(report.sourceGeneratedAt?.[source],'Fecha de fuente '+source);
   need(Array.isArray(report.sectors)&&report.sectors.length===rows.length,'Sectores del reporte ausentes o incompletos');ids(report.sectors.map(r=>r.sectorId),rows.map(r=>r.sector),'Sectores del reporte');
   const totals=counts();let capacity=0,available=0,admissions=0;
   for(const row of rows){const sector=report.sectors.find(r=>r.sectorId===row.sector);equal(sector.inventoryState,'HABILITADO','Estado de inventario reportado');equal(sector.ticketsMatchSales,true,'Tickets y ventas del sector');metrics(sector.metrics,row.metrics,'Sector '+row.sector,row.capacity,row.expectedAvailable);for(const field of Object.keys(totals))totals[field]+=row.metrics[field];capacity+=row.capacity;available+=row.expectedAvailable;admissions+=sector.metrics.admissions;}
   metrics(report.totals,totals,'Totales de '+key,capacity,available);equal(report.totals.admissions,admissions,'Total de admisiones');
  }
  // Distributed GETs are not an atomic snapshot; fail closed across a TTL boundary.
  need(pendingDeadlines.every(t=>t>Date.now()),'La reserva temporal venció durante la verificación; repetir solo lecturas tras el scheduler');
  const result={result:'PASS',project:c.meta.project,checkedAt:new Date().toISOString(),gateway:'http://127.0.0.1:49080',events,scenarios,limitations:['Lecturas distribuidas no atómicas; una transición concurrente requiere repetir la verificación, nunca reparar datos.','Las APIs verifican proyecciones de pagos y tickets; no exponen el diario interno de la pasarela ni el lote de emisión para una prueba SQL independiente de unicidad.','Las preferencias se comprueban mediante la API JWT respaldada por Mongo; no se consulta directamente MongoDB.','Admisiones solo se concilian dentro del reporte porque TicketResponse no expone validatedAt.','SSE y entrega asíncrona de avisos no se verifican con estas lecturas.']};d.save(path.join(d.runtime,'verification.json'),result);console.log('PASS: catálogo, pagos e importes, etapas finales, reservas, inventario conciliado, devolución, preferencias y reportes coherentes.');return result;
 }catch(error){
  const reason=error instanceof VerificationFailure?error.message:'FAIL: '+phase+': respuesta inválida, precondición incumplida o consulta no disponible';
  console.error(reason);if(accredited){try{d.save(path.join(d.runtime,'verification.json'),{result:'FAIL',project:c.meta.project,checkedAt:new Date().toISOString(),phase,message:reason});}catch{console.error('FAIL: no se pudo guardar el informe de verificación');}}
  throw new VerificationFailure(reason);
 }
}
module.exports={verify};if(require.main===module)d.main(()=>verify());
