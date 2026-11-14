package ar.edu.unicen.grupo8.comprasservice;
import org.junit.jupiter.api.Test;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.beans.factory.annotation.Value;
import java.net.*;import java.net.http.*;import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("test")
class DistributedSecurityHttpTest {
 @Value("${local.server.port}") int port;
 int get(String path,String token)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("X-Admin-Id","41001").header("X-User-Role","ADMIN");if(token!=null)b.header("Authorization","Bearer "+token);return HttpClient.newHttpClient().send(b.GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode();}
 @Test void noTokenAndInvalidTokensReturn401()throws Exception{
  assertEquals(401,get("/internal/orders/999",null));assertEquals(401,get("/internal/orders/999","invalid"));
  assertEquals(401,get("/internal/orders/999",SecurityTestKeys.token("compras","user","1","CLIENTE",null,-60)));
  assertEquals(401,get("/internal/orders/999",SecurityTestKeys.token("wrong","user","1","CLIENTE",null,300)));
 }
 @Test void ownershipPathsStayClosedAndForgedHeadersDoNotGrantAccess()throws Exception{
  assertEquals(401,get("/compras/9223372036854775807",null));
  assertEquals(404,get("/compras/9223372036854775807",SecurityTestKeys.token("compras","user","1","ADMINISTRADOR",null,300)));
 }
 @Test void internalRoutesEnforceTypeScopeAndCaller()throws Exception{
  assertEquals(403,get("/internal/orders/999",SecurityTestKeys.token("compras","user","1","CLIENTE",null,300)));
  assertEquals(403,get("/internal/orders/999",SecurityTestKeys.token("compras","service","service:entradas-to-compras",null,"wrong.scope",120)));
  assertEquals(403,get("/internal/orders/999",SecurityTestKeys.token("compras","service","service:eventos-to-compras",null,"orders.read",120)));
  assertEquals(404,get("/internal/orders/999",SecurityTestKeys.token("compras","service","service:entradas-to-compras",null,"orders.read",120)));
 }
}
