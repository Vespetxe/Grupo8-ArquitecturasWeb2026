package grupo22.eventocatalogo_service;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import java.net.*;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"springdoc.api-docs.enabled=true","springdoc.api-docs.path=/api-docs"})
class OpenApiSecurityHttpTest {
 @Value("${local.server.port}") int port;
 HttpResponse<String> get(String path,String token)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(20));
  if(token!=null)b.header("Authorization","Bearer "+token);
  return HttpClient.newHttpClient().send(b.GET().build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void onlyValidatedHumanAdministratorCanReadRealSpringdocEndpoint()throws Exception{
  assertEquals(401,get("/api-docs",null).statusCode());
  assertEquals(401,get("/api-docs","invalid").statusCode());
  assertEquals(401,get("/api-docs",SecurityTestKeys.token("eventos","user","1","ADMINISTRADOR",null,-60)).statusCode());
  assertEquals(403,get("/api-docs",SecurityTestKeys.token("eventos","user","1","CLIENTE",null,300)).statusCode());
  assertEquals(403,get("/api-docs",SecurityTestKeys.token("eventos","service","service:compras-to-eventos",null,"events.read",120)).statusCode());
  var response=get("/api-docs",SecurityTestKeys.token("eventos","user","1","ADMINISTRADOR",null,300));
  assertEquals(200,response.statusCode());assertTrue(response.body().contains("\"openapi\""));assertTrue(response.body().contains("/api/v1/events"));
  assertEquals(403,get("/api-docs/swagger-config",SecurityTestKeys.token("eventos","user","1","CLIENTE",null,300)).statusCode());
 }
}
