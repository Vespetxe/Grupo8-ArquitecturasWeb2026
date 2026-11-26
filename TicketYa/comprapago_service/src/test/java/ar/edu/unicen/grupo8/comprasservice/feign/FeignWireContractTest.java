package ar.edu.unicen.grupo8.comprasservice.feign;
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
   exchange.getResponseHeaders().set("Content-Type","application/json");byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,status==204?-1:bytes.length);if(status!=204)exchange.getResponseBody().write(bytes);exchange.close();
  });server.start();
  try(var bounded=new ar.edu.unicen.grupo8.comprasservice.timing.TimedTransport(new org.springframework.mock.env.MockEnvironment())){String url="http://127.0.0.1:"+server.getAddress().getPort();var client=FeignSupport.transport(bounded.factory(false),bounded.propagate());

 var event=FeignSupport.target(ComprasEventosFeignClient.class,client,url);org.junit.jupiter.api.Assertions.assertEquals(1L,event.get(1L).eventId());
 var inventory=FeignSupport.target(ComprasInventarioFeignClient.class,client,url);org.junit.jupiter.api.Assertions.assertEquals(1L,inventory.get(1L).idReserva());inventory.confirm(1L);inventory.release(1L);inventory.compensate(1L);inventory.giveBack(1L,java.util.Map.of("entradas",java.util.List.of()));org.junit.jupiter.api.Assertions.assertEquals(0,inventory.returned(1L).length);
 var tickets=FeignSupport.target(ComprasEntradasFeignClient.class,client,url);org.junit.jupiter.api.Assertions.assertEquals(0,tickets.get(1L).length);tickets.issue(java.util.Map.of("orderId","1"));tickets.annul(1L);tickets.status("00000000-0000-4000-8000-000000000001",java.util.Map.of("orderId","1","status","ANULADA"));

   org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("GET /api/v1/events/1","GET /reservas/1","POST /reservas/1/confirmar","POST /reservas/1/liberar","POST /internal/reservas/1/compensar","POST /internal/reservas/1/devoluciones","GET /internal/reservas/1/devoluciones","GET /tickets?orderId=1","POST /internal/tickets/issue","POST /internal/tickets/orders/1/annul","PATCH /internal/tickets/00000000-0000-4000-8000-000000000001/status"),received);org.junit.jupiter.api.Assertions.assertEquals(11,received.size());
  }finally{server.stop(0);}
 }
}
