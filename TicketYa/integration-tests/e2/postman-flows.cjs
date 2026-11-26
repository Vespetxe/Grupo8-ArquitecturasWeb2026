'use strict';
// Declarative Collection Runner/Newman flow. All IDs come from real responses.
function items(){
 const list=[];
 function add(name,method,route,body,role,status,capture=[],checks=[]){
  const request={method,url:'{{gatewayUrl}}'+route,header:[{key:'Accept',value:'application/json'}]};
  if(role)request.header.push({key:'Authorization',value:'Bearer {{jwt_'+role+'}}'});
  if(body!==undefined){request.header.push({key:'Content-Type',value:'application/json'});request.body={mode:'raw',raw:JSON.stringify(body).replace(/"(\{\{(?:clientId|organizerId|accessId|venueId|sectorId|eventId|reservationId|purchaseId|refundId|numericTicketId|seat1|seat2)\}\})"/g,'$1'),options:{raw:{language:'json'}}};}
  const exec=[`pm.test('HTTP ${status}',()=>pm.expect(pm.response.code).to.eql(${status}));`,'var responseBody=pm.response.text()?pm.response.json():null;',...capture.map(([key,expression])=>`pm.collectionVariables.set('${key}',${expression});`),...checks.map(code=>`pm.test('${name.replaceAll("'",'')} — estado',()=>{${code}});`)];
  list.push({name,request,event:[{listen:'test',script:{type:'text/javascript',exec}}]});
 }
 add('Registrar cliente nuevo','POST','/auth/register',{email:'{{clientEmail}}',password:'{{clientPassword}}'},null,201);
 for(const role of ['cliente','organizador','administrador','personal_acceso']){
  add('Login '+role,'POST','/auth/login',{email:role==='cliente'?'{{clientEmail}}':'{{'+role+'Email}}',password:role==='cliente'?'{{clientPassword}}':'{{'+role+'Password}}'},null,200,[['jwt_'+role,'responseBody.access_token']]);
  if(role!=='administrador')add('Identidad '+role,'GET','/auth/me',undefined,role,200,[[{cliente:'clientId',organizador:'organizerId',personal_acceso:'accessId'}[role],'responseBody.id']]);
 }
 add('Crear participante','POST','/api/v1/participants',{name:'Feign {{runId}}',role:'Banda'},'administrador',201,[['participantId','responseBody.participantId']]);
 add('Crear lugar','POST','/api/v1/venues',{name:'Postman {{runId}}',address:'Prueba aislada',city:'Tandil',latitude:-37.328,longitude:-59.137},'organizador',201,[['venueId','responseBody.venueId']]);
 add('Crear sector numerado','POST','/api/v1/venues/{{venueId}}/sectors',{name:'Platea',type:'NUMERADO',capacity:2,rows:1,seatsPerRow:2},'organizador',201,[['sectorId','responseBody.sectorId']]);
 function event(label,price){
  add('Crear evento '+label,'POST','/api/v1/events',{name:'Postman '+label+' {{runId}}',eventType:'Recital',organizerId:'{{organizerId}}',venueId:'{{venueId}}',startDateTime:'{{futureDate}}',participantIds:[]},'organizador',201,[['eventId','responseBody.eventId']],[`pm.expect(responseBody.status).to.eql('BORRADOR');`]);
  add('Precio '+label,'POST','/api/v1/events/{{eventId}}/prices',[{sectorId:'{{sectorId}}',price}],'organizador',201);
  add('Publicar '+label,'PATCH','/api/v1/events/{{eventId}}/publish',undefined,'organizador',200,[],[`pm.expect(responseBody.status).to.eql('PUBLICADO');`]);
  add('Disponibilidad '+label,'GET','/disponibilidad/eventos/{{eventId}}/sectores/{{sectorId}}',undefined,null,200,[['seat1','responseBody.asientosLibres[0].idAsiento'],['seat2','responseBody.asientosLibres[1].idAsiento']]);
 }
 function buy(quantity,label){
  const ids=quantity===2?['{{seat1}}','{{seat2}}']:['{{seat1}}'];
  add('Reservar '+label,'POST','/reservas',{idCliente:'{{clientId}}',idEvento:'{{eventId}}',idSector:'{{sectorId}}',cantidad:quantity,idsAsientos:ids},'cliente',201,[['reservationId','responseBody.idReserva']],[`pm.expect(responseBody.estado).to.eql('PENDIENTE');`]);
  add('Comprar '+label,'POST','/compras',{clienteId:'{{clientId}}',eventoId:'{{eventId}}',reservaId:'{{reservationId}}',detalles:ids.map(asientoId=>({sectorId:'{{sectorId}}',asientoId,cantidad:1}))},'cliente',201,[['purchaseId','responseBody.id'],['purchase'+label,'responseBody.id']],[`pm.expect(responseBody.estado).to.eql('PENDIENTE');`]);
 }
 event('A',15000);buy(2,'A');
 const payment={compraId:'{{purchaseId}}',claveIdempotencia:'postman-{{runId}}',medioPago:'TARJETA_DEBITO'};
 add('Pago aprobado','POST','/pagos',payment,'cliente',201,[['originalPaymentId','responseBody.id']],[`pm.expect(responseBody.estado).to.eql('APROBADO');pm.expect(responseBody.importe).to.eql(30000);`]);
 add('Replay mismo pago','POST','/pagos',payment,'cliente',200,[],`pm.expect(responseBody.id).to.eql(Number(pm.collectionVariables.get('originalPaymentId')));`.split('\n'));
 add('Tickets emitidos','GET','/tickets?orderId={{purchaseId}}',undefined,'cliente',200,[['qr','responseBody[1].qr'],['ticketToReturnId','responseBody[0].ticketId']],[`pm.expect(responseBody).to.have.lengthOf(2);pm.expect(new Set(responseBody.map(t=>t.ticketId)).size).to.eql(2);`]);
 add('IDs numéricos de entradas','GET','/compras/{{purchaseId}}/entradas',undefined,'cliente',200,[['numericTicketId',"responseBody.find(e=>e.ticketId===pm.collectionVariables.get('ticketToReturnId')).entradaId"]]);
 add('Solicitar devolución parcial','POST','/devoluciones',{compraId:'{{purchaseId}}',clienteId:'{{clientId}}',entradasIds:['{{numericTicketId}}'],motivo:'Prueba Postman parcial'},'cliente',201,[['refundId','responseBody.id']]);
 add('Aprobar devolución','POST','/admin/devoluciones/{{refundId}}/aprobar',{motivo:'Prueba autorizada'},'administrador',200);
 const refund={claveIdempotencia:'postman-refund-{{runId}}',importe:15000};
 add('Procesar reintegro','POST','/admin/devoluciones/{{refundId}}/procesar',refund,'administrador',200,[['originalRefundId','responseBody.reintegro.id']],[`pm.expect(responseBody.etapa).to.eql('FINALIZADA');pm.expect(responseBody.reintegro.importe).to.eql(15000);`]);
 add('Replay mismo reintegro','POST','/admin/devoluciones/{{refundId}}/procesar',refund,'administrador',200,[],[`pm.expect(responseBody.reintegro.id).to.eql(Number(pm.collectionVariables.get('originalRefundId')));`]);
 add('Asignar personal','PUT','/admin/accesos/eventos/{{eventId}}/personal/{{accessId}}',undefined,'administrador',200);
 add('QR aceptado','POST','/accesos/eventos/{{eventId}}/validaciones',{qr:'{{qr}}'},'personal_acceso',200,[],['pm.expect(responseBody.accepted).to.eql(true);']);
 add('QR repetido rechazado','POST','/accesos/eventos/{{eventId}}/validaciones',{qr:'{{qr}}'},'personal_acceso',409,[],["pm.expect(responseBody.code).to.eql('ALREADY_USED');"]);
 add('Reporte parcial','GET','/api/v1/events/{{eventId}}/report',undefined,'organizador',200,[],['pm.expect(responseBody.totals.grossCollected).to.eql(30000);pm.expect(responseBody.totals.refundedAmount).to.eql(15000);pm.expect(responseBody.totals.netCollected).to.eql(15000);']);
 add('Cancelar evento','PATCH','/api/v1/events/{{eventId}}/cancel',undefined,'administrador',200,[],["pm.expect(responseBody.status).to.eql('CANCELADO');"]);
 event('Rejected',150000);buy(1,'Rejected');
 add('Pago rechazado','POST','/pagos',{compraId:'{{purchaseId}}',claveIdempotencia:'postman-rejected-{{runId}}',medioPago:'TARJETA_DEBITO'},'cliente',201,[],["pm.expect(responseBody.estado).to.eql('RECHAZADO');"]);
 event('Uncertain',15000);buy(1,'Uncertain');
 const uncertain={compraId:'{{purchaseId}}',claveIdempotencia:'simular-timeout-postman-{{runId}}',medioPago:'TARJETA_DEBITO'};
 add('Pago incierto','POST','/pagos',uncertain,'cliente',201,[['uncertainPaymentId','responseBody.id']],["pm.expect(responseBody.estado).to.eql('RESULTADO_DESCONOCIDO');"]);
 add('Recuperar intento original','POST','/pagos',uncertain,'cliente',200,[],["pm.expect(responseBody.estado).to.eql('APROBADO');pm.expect(responseBody.id).to.eql(Number(pm.collectionVariables.get('uncertainPaymentId')));"]);
 add('Marcar agotado','PATCH','/api/v1/events/{{eventId}}/status?status=AGOTADO',undefined,'administrador',200,[],["pm.expect(responseBody.status).to.eql('AGOTADO');"]);
 add('Rehabilitar inventario','PATCH','/api/v1/events/{{eventId}}/status?status=PUBLICADO',undefined,'administrador',200,[],["pm.expect(responseBody.status).to.eql('PUBLICADO');"]);
 add('Preferencias','PUT','/api/v1/me/preferences',{favoriteTypes:['Recital'],favoriteParticipantIds:[],preferredCity:'Tandil'},'cliente',200);
 add('Recomendaciones','GET','/api/v1/me/recommendations',undefined,'cliente',200,[],['pm.expect(responseBody.events.length).to.be.at.most(10);']);
 add('Bandeja','GET','/api/v1/me/notifications',undefined,'cliente',200,[],['pm.expect(responseBody).to.be.an("array");']);
 return list;
}
function setup(){return {listen:'prerequest',script:{type:'text/javascript',exec:["if(!pm.collectionVariables.get('runId'))pm.collectionVariables.set('runId',pm.variables.replaceIn('{{$randomUUID}}'));","if(!pm.collectionVariables.get('futureDate'))pm.collectionVariables.set('futureDate',new Date(Date.now()+8*86400000).toISOString());","if(!pm.collectionVariables.get('clientEmail'))pm.collectionVariables.set('clientEmail','postman-'+pm.collectionVariables.get('runId')+'@example.test');","if(!pm.collectionVariables.get('clientPassword'))pm.collectionVariables.set('clientPassword',pm.variables.replaceIn('{{$randomUUID}}')+pm.variables.replaceIn('{{$randomUUID}}'));"]}};}
module.exports={items,setup};
