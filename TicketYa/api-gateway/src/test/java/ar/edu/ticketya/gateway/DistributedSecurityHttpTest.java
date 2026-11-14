package ar.edu.ticketya.gateway;
import org.junit.jupiter.api.Test;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.beans.factory.annotation.Value;
import java.net.*;import java.net.http.*;import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)

class DistributedSecurityHttpTest {
 @Value("${local.server.port}") int port;
 int get(String path,String token)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("X-Admin-Id","41001").header("X-User-Role","ADMIN");if(token!=null)b.header("Authorization","Bearer "+token);return HttpClient.newHttpClient().send(b.GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode();}
 @Test void noTokenAndInvalidTokensReturn401()throws Exception{
  assertEquals(401,get("/internal/orders/999",null));assertEquals(401,get("/internal/orders/999","invalid"));
  assertEquals(401,get("/internal/orders/999",SecurityTestKeys.token("gateway","user","1","CLIENTE",null,-60)));
  assertEquals(401,get("/internal/orders/999",SecurityTestKeys.token("wrong","user","1","CLIENTE",null,300)));
 }
 @Test void ownershipPathsStayClosedAndForgedHeadersDoNotGrantAccess()throws Exception{
  assertEquals(401,get("/compras/1",null));
  assertEquals(503,get("/compras/1",SecurityTestKeys.token("gateway","user","1","ADMINISTRADOR",null,300)));
  assertEquals(403,get("/compras/1",SecurityTestKeys.token("gateway","user","1","PERSONAL_ACCESO",null,300)));
 }
 @Test void internalRoutesEnforceTypeScopeAndCaller()throws Exception{
  assertEquals(403,get("/internal/orders/999",SecurityTestKeys.token("gateway","user","1","CLIENTE",null,300)));
  assertEquals(401,get("/internal/orders/999",SecurityTestKeys.token("gateway","service","service:entradas-to-gateway",null,"wrong.scope",120)));
  assertEquals(401,get("/internal/orders/999",SecurityTestKeys.token("gateway","service","service:eventos-to-gateway",null,"orders.read",120)));
  assertEquals(401,get("/internal/orders/999",SecurityTestKeys.token("gateway","service","service:entradas-to-gateway",null,"orders.read",120)));
 }
}
