package ar.edu.tudai.ticketya.servicio_entradas;
import org.junit.jupiter.api.Test;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.beans.factory.annotation.Value;
import java.net.*;import java.net.http.*;import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)

class DistributedSecurityHttpTest {
 @org.springframework.test.context.bean.override.mockito.MockitoBean ar.edu.tudai.ticketya.servicio_entradas.integration.OrderClient orders;
 @org.junit.jupiter.api.BeforeEach void missingOrder(){org.mockito.Mockito.when(orders.getOrderForOwnership(org.mockito.ArgumentMatchers.anyString())).thenThrow(new ar.edu.tudai.ticketya.servicio_entradas.integration.OrderNotFoundException("999"));}
 @Value("${local.server.port}") int port;
 int get(String path,String token)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("X-Admin-Id","41001").header("X-User-Role","ADMIN");if(token!=null)b.header("Authorization","Bearer "+token);return HttpClient.newHttpClient().send(b.GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode();}
 @Test void noTokenAndInvalidTokensReturn401()throws Exception{
  assertEquals(401,get("/tickets?orderId=999",null));assertEquals(401,get("/tickets?orderId=999","invalid"));
  assertEquals(401,get("/tickets?orderId=999",SecurityTestKeys.token("entradas","user","1","CLIENTE",null,-60)));
  assertEquals(401,get("/tickets?orderId=999",SecurityTestKeys.token("wrong","user","1","CLIENTE",null,300)));
 }
 @Test void ownershipPathsStayClosedAndForgedHeadersDoNotGrantAccess()throws Exception{
  assertEquals(401,get("/compras/1",null));
  assertEquals(403,get("/compras/1",SecurityTestKeys.token("entradas","user","1","ADMINISTRADOR",null,300)));
 }
 @Test void internalRoutesEnforceTypeScopeAndCaller()throws Exception{
  assertEquals(404,get("/tickets?orderId=999",SecurityTestKeys.token("entradas","user","1","CLIENTE",null,300)));
  assertEquals(403,get("/tickets?orderId=999",SecurityTestKeys.token("entradas","service","service:compras-to-entradas",null,"wrong.scope",120)));
  assertEquals(403,get("/tickets?orderId=999",SecurityTestKeys.token("entradas","service","service:eventos-to-entradas",null,"tickets.read",120)));
  assertEquals(200,get("/tickets?orderId=999",SecurityTestKeys.token("entradas","service","service:compras-to-entradas",null,"tickets.read",120)));
 }

 @Test void emptyTicketsRequireOwnerWithoutWeakeningIssuanceEligibility() throws Exception {
  var order=new ar.edu.tudai.ticketya.servicio_entradas.integration.OrderSnapshot("42","1","PENDIENTE",java.util.List.of(new ar.edu.tudai.ticketya.servicio_entradas.integration.OrderItemSnapshot("1","1",null,1)),1L);
  org.mockito.Mockito.doReturn(order).when(orders).getOrderForOwnership("42");
  assertEquals(200,get("/tickets?orderId=42",SecurityTestKeys.token("entradas","user","1","CLIENTE",null,300)));
  assertEquals(404,get("/tickets?orderId=42",SecurityTestKeys.token("entradas","user","2","CLIENTE",null,300)));
  assertEquals(403,get("/tickets?orderId=42",SecurityTestKeys.token("entradas","user","1","PERSONAL_ACCESO",null,300)));
 }

}
