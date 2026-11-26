package grupo22.eventocatalogo_service.feign;
class FeignWireContractTest {
 @org.junit.jupiter.api.Test void everyDeclaredBusinessOperationUsesFeignHttp()throws Exception{
  var received=new java.util.concurrent.CopyOnWriteArrayList<String>();
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",exchange->{String method=exchange.getRequestMethod(),route=exchange.getRequestURI().getPath();received.add(method+" "+exchange.getRequestURI());byte[] request=exchange.getRequestBody().readAllBytes();if(request.length>0)org.junit.jupiter.api.Assertions.assertNotNull(new tools.jackson.databind.ObjectMapper().readTree(request));
   String body="{}";int status=204;
   if(method.equals("GET")){status=200;if(route.equals("/api/v1/events/1"))body="{\"eventId\":1,\"status\":\"PUBLICADO\",\"startDateTime\":\"2030-01-01T00:00:00Z\",\"sectorPrices\":[]}";
    else if(route.equals("/reservas/1"))body="{\"idReserva\":1,\"idCliente\":1,\"idEvento\":1,\"idSector\":1,\"cantidad\":1,\"fechaExpiracion\":\"2030-01-01T00:00:00\",\"estado\":\"PENDIENTE\",\"asientosReservados\":[],\"identityOwnerId\":1}";
    else if(route.equals("/internal/orders/1"))body="{\"orderId\":\"1\",\"clientId\":\"1\",\"status\":\"CONFIRMADA\",\"items\":[]}";
    else if(route.endsWith("/history"))body="{\"eventIds\":[1]}";
    else if(route.contains("/reports/"))body="{\"eventId\":1,\"generatedAt\":\"2030-01-01T00:00:00Z\",\"sectors\":[]}";
    else body="[]";
   }else if(route.equals("/eventos/1/inventario")){status=200;body="{\"exitoso\":true}";}else if(route.equals("/admin/eventos/1/cancelacion")){status=202;body="{\"eventoId\":1,\"devoluciones\":[],\"comprasPendientes\":[]}";}else if(route.equals("/internal/notifications/events")){status=200;body="{\"accepted\":true}";}
   if(route.endsWith("/sales"))body="{\"eventId\":1,\"generatedAt\":\"2030-01-01T00:00:00Z\",\"financialComplete\":true,\"sectors\":[]}";
   exchange.getResponseHeaders().set("Content-Type","application/json");byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,status==204?-1:bytes.length);if(status!=204)exchange.getResponseBody().write(bytes);exchange.close();
  });server.start();
  try(var bounded=new grupo22.eventocatalogo_service.timing.TimedTransport(new org.springframework.mock.env.MockEnvironment())){String url="http://127.0.0.1:"+server.getAddress().getPort();var client=FeignSupport.transport(bounded.factory(false),bounded.propagate());

 var inventory=FeignSupport.target(EventosInventarioFeignClient.class,client,url);
 org.junit.jupiter.api.Assertions.assertTrue(inventory.initialize(1L,new grupo22.eventocatalogo_service.service.EventWorkflow.StockSnapshot(1L,false,2,java.util.List.of())).path("exitoso").asBoolean());
 inventory.publish(1L);inventory.suspend(1L);inventory.enable(1L);inventory.cancel(1L);org.junit.jupiter.api.Assertions.assertEquals(1L,inventory.report(1L).eventId());
 var tickets=FeignSupport.target(EventosEntradasFeignClient.class,client,url);tickets.block(1L);org.junit.jupiter.api.Assertions.assertEquals(1L,tickets.report(1L).eventId());
 var purchases=FeignSupport.target(EventosComprasFeignClient.class,client,url);org.junit.jupiter.api.Assertions.assertEquals(202,purchases.cancel(1L).getStatusCode().value());org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(1L),purchases.history(1L).eventIds());
 org.junit.jupiter.api.Assertions.assertTrue(purchases.notify(new grupo22.eventocatalogo_service.notifications.EventNoticeDispatcher.Delivery(1L,1L,"EVENTO_CANCELADO",java.time.Instant.parse("2030-01-01T00:00:00Z"),java.util.List.of())).accepted());org.junit.jupiter.api.Assertions.assertEquals(1L,purchases.report(1L).eventId());

   org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("POST /eventos/1/inventario","POST /internal/eventos/1/publicar","POST /internal/eventos/1/suspender","POST /internal/eventos/1/habilitar","POST /eventos/1/cancelar-inventario","GET /internal/reports/events/1/inventory","POST /internal/events/1/cancel-emission","GET /internal/reports/events/1/tickets","POST /admin/eventos/1/cancelacion","GET /internal/recommendations/customers/1/history","POST /internal/notifications/events","GET /internal/reports/events/1/sales"),received);org.junit.jupiter.api.Assertions.assertEquals(12,received.size());
  }finally{server.stop(0);}
 }
}
