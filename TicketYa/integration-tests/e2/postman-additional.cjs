'use strict';
// Positive cases missing from the main flow. Only isolated fixtures; no new charge/refund.
function items(){
 const list=[];
 function add(name,method,route,role,status,body,capture=[],checks=[]){const request={method,url:route.startsWith('http')?route:'{{gatewayUrl}}'+route,header:[{key:'Accept',value:'application/json'},{key:'Authorization',value:'Bearer {{'+(role.startsWith('token_')?role:'jwt_'+role)+'}}'}]};if(body!==undefined){request.header.push({key:'Content-Type',value:'application/json'});request.body={mode:'raw',raw:typeof body==='string'?body:JSON.stringify(body)};}list.push({name,request,event:[{listen:'test',script:{type:'text/javascript',exec:[`pm.test('HTTP ${status}',()=>pm.expect(pm.response.code).to.eql(${status}));`,'var responseBody=pm.response.text()?pm.response.json():null;',...capture.map(([key,value])=>`pm.collectionVariables.set('${key}',${value});`),...checks.map(code=>`pm.test('Contrato ${name}',()=>{${code}});`)]}}]});}
 add('Identificar evento original','GET','/compras/{{purchaseUncertain}}','cliente',200,undefined,[['eventId','responseBody.eventoId']]);
 add('Crear participante descartable','POST','/api/v1/participants','administrador',201,{name:'Complementario {{runId}}',role:'Banda'},[['participantId','responseBody.participantId']]);
 add('Listar reservas propias','GET','/reservas?idCliente={{clientId}}','cliente',200,undefined,[],['pm.expect(responseBody).to.be.an("array");']);
 add('Listar compras propias','GET','/compras?clienteId={{clientId}}','cliente',200,undefined,[],['var owner=Number(pm.collectionVariables.get("clientId"));pm.expect(responseBody.every(c=>c.clienteId===owner)).to.eql(true);']);
 add('Estado de compra efectiva','GET','/compras/{{purchaseUncertain}}/estado','cliente',200,undefined,[],['pm.expect(responseBody.estado).to.eql("COMPLETADA");']);
 add('Detalle histórico','GET','/compras/{{purchaseUncertain}}/detalle','cliente',200,undefined,[['sectorId','responseBody[0].sectorId']],['pm.expect(responseBody).to.have.lengthOf(1);']);
 add('Consultar devolución procesada','GET','/devoluciones/{{refundId}}','administrador',200,undefined,[],['pm.expect(responseBody.id).to.eql(Number(pm.collectionVariables.get("refundId")));']);
  add('Consultar procesamiento','GET','/admin/devoluciones/{{refundId}}/procesamiento','administrador',200,undefined,[['originalRefundId','responseBody.reintegro.id']],['pm.expect(responseBody.etapa).to.eql("FINALIZADA");']);
 add('Consultar reintegro','GET','/admin/reintegros/{{originalRefundId}}','administrador',200,undefined,[],['pm.expect(responseBody.estado).to.eql("COMPLETADO");pm.expect(responseBody.importe).to.eql(15000);']);
 add('Seleccionar entrada sin devolver','GET','/compras/{{purchaseUncertain}}/entradas','cliente',200,undefined,[['candidateTicketId','responseBody[0].entradaId']]);
 add('Solicitud para rechazar','POST','/devoluciones','cliente',201,'{"compraId":{{purchaseUncertain}},"clienteId":{{clientId}},"entradasIds":[{{candidateTicketId}}],"motivo":"Prueba rechazo administrativo"}',[['rejectedRequestId','responseBody.id']]);
 add('Rechazo administrativo','POST','/admin/devoluciones/{{rejectedRequestId}}/rechazar','administrador',200,{motivo:'Prueba administrativa sin reintegro'},[],['pm.expect(responseBody.estado).to.eql("RECHAZADA");']);
 add('Disponibilidad para cancelar sin pagar','GET','/disponibilidad/eventos/{{eventId}}/sectores/{{sectorId}}','cliente',200,undefined,[['reserveSeats',"responseBody.asientosLibres?',\"idsAsientos\":['+responseBody.asientosLibres[0].idAsiento+']':''"],['detailSeat',"responseBody.asientosLibres?',\"asientoId\":'+responseBody.asientosLibres[0].idAsiento:''"]],[ 'pm.expect(responseBody.disponibles).to.eql(1);']);
 add('Reserva para cancelación pendiente','POST','/reservas','cliente',201,'{"idCliente":{{clientId}},"idEvento":{{eventId}},"idSector":{{sectorId}},"cantidad":1{{reserveSeats}}}',[['cancelReservationId','responseBody.idReserva']]);
 add('Compra sin pago','POST','/compras','cliente',201,'{"clienteId":{{clientId}},"eventoId":{{eventId}},"reservaId":{{cancelReservationId}},"detalles":[{"sectorId":{{sectorId}},"cantidad":1{{detailSeat}}}]}',[['unpaidPurchaseId','responseBody.id']]);
 add('Cancelar compra pendiente','POST','/compras/{{unpaidPurchaseId}}/cancelar','cliente',200,undefined,[],['pm.expect(responseBody.estado).to.eql("CANCELADA");']);
 add('Confirmar liberación de reserva','GET','/reservas/{{cancelReservationId}}','cliente',200,undefined,[],['pm.expect(responseBody.estado).to.eql("LIBERADA");']);
 add('Lugar y organizador para cola','GET','/api/v1/events/{{eventId}}','organizador',200,undefined,[['venueId','responseBody.venueId'],['organizerId','responseBody.organizerId']]);
 add('Crear evento sin reservas para cola','POST','/api/v1/events','organizador',201,'{"name":"Cola abandono {{runId}}","eventType":"Recital","organizerId":{{organizerId}},"venueId":{{venueId}},"startDateTime":"{{futureDate}}","participantIds":[]}',[['queueEventId','responseBody.eventId']]);
 add('Precio del evento de cola','POST','/api/v1/events/{{queueEventId}}/prices','organizador',201,'[{"sectorId":{{sectorId}},"price":15000}]');
 add('Publicar evento de cola','PATCH','/api/v1/events/{{queueEventId}}/publish','organizador',200);
 add('Configurar cola para abandono','PUT','/colas/eventos/{{queueEventId}}','administrador',200,{tamanoTanda:1,turnoSegundos:120});
 add('Ingresar para abandonar','POST','/colas/eventos/{{queueEventId}}','cliente',200);
 add('Abandonar turno propio','DELETE','/colas/eventos/{{queueEventId}}/mi-turno','cliente',200,undefined,[],['pm.expect(responseBody.estado).not.to.eql("HABILITADO");']);
 add('Borrar participante sin relaciones','DELETE','/api/v1/participants/{{participantId}}','administrador',204);
 add('Datos originales de compra devuelta','GET','/compras/{{purchaseA}}','administrador',200,undefined,[['oldReservation','responseBody.reservaId'],['oldEventId','responseBody.eventoId']]);
 add('Entradas ya anuladas','GET','/compras/{{purchaseA}}/entradas','administrador',200,undefined,[['annulledTicket','responseBody[0].ticketId'],['oldSeat','responseBody[0].asientoId'],['oldSectorId','responseBody[0].sectorId']]);
 for(const[target,scopes]of [['inventario','reservations.compensate reservations.return'],['entradas','tickets.status tickets.annul']])list.push({name:'OAuth2 lectura/replay '+target,request:{method:'POST',url:'http://identidad:8085/oauth2/token',auth:{type:'basic',basic:[{key:'username',value:'compras-to-'+target,type:'string'},{key:'password',value:'{{secret_compras_'+target+'}}',type:'string'}]},body:{mode:'urlencoded',urlencoded:[{key:'grant_type',value:'client_credentials'},{key:'scope',value:scopes}]}},event:[{listen:'test',script:{type:'text/javascript',exec:[`pm.test('OAuth2 permitido',()=>pm.expect(pm.response.code).to.eql(200));pm.collectionVariables.set('token_compras_${target}',pm.response.json().access_token);`]}}]});
 add('Compensación idempotente ya liberada','POST','http://inventario:8082/internal/reservas/{{cancelReservationId}}/compensar','token_compras_inventario',200,undefined,[],['pm.expect(responseBody.exitoso).to.eql(true);']);
 add('Replay devolución histórica','POST','http://inventario:8082/ocupaciones/liberar','token_compras_inventario',200,'{"idReserva":{{oldReservation}},"entradasIds":["{{annulledTicket}}"],"idEvento":{{oldEventId}},"idSector":{{oldSectorId}},"cantidad":1,"idsAsientos":[{{oldSeat}}]}',[],['pm.expect(responseBody.exitoso).to.eql(true);']);
 add('Replay estado por evento','PATCH','http://entradas:8083/internal/tickets/status','token_compras_entradas',200,{orderId:'{{purchaseA}}',eventId:'{{oldEventId}}',status:'ANULADA'},[],['pm.expect(responseBody.every(t=>t.status==="ANULADA")).to.eql(true);']);
 add('Replay anulación completa','POST','http://entradas:8083/internal/tickets/orders/{{purchaseA}}/annul','token_compras_entradas',200);
 add('Stock conservado tras replays','GET','/disponibilidad/eventos/{{eventId}}/sectores/{{sectorId}}','cliente',200,undefined,[],['pm.expect(responseBody.disponibles).to.eql(1);']);
 return list;
}
module.exports={items};
