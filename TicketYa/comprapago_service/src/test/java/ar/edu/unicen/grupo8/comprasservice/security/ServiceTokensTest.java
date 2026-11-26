package ar.edu.unicen.grupo8.comprasservice.security;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.net.*;import java.time.*;import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class ServiceTokensTest {
 @Test void cachesRenewsAndNeverReplaysBusinessWritesOnAuthenticationFailure()throws Exception{
  var clockValue=new AtomicReference<>(Instant.now());
  Clock clock=new Clock(){public java.time.ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(java.time.ZoneId z){return this;}public Instant instant(){return clockValue.get();}};
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  var issued=new AtomicInteger();var business=new AtomicInteger();
  server.createContext("/oauth2/token",e->{String request=new String(e.getRequestBody().readAllBytes());assertTrue(request.contains("scope="));assertTrue(request.contains("reservations.read"));byte[] b=("{\"access_token\":\"test-token-"+issued.incrementAndGet()+"\",\"token_type\":\"Bearer\",\"expires_in\":3600}").getBytes();e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
  server.createContext("/write",e->{assertNotNull(e.getRequestHeaders().getFirst("Authorization"));business.incrementAndGet();e.getRequestBody().readAllBytes();e.sendResponseHeaders(401,-1);e.close();});server.start();
  try{
   var env=new MockEnvironment().withProperty("TY_SECURITY_TOKEN_URL","http://127.0.0.1:"+server.getAddress().getPort()+"/oauth2/token");
   for(String target:new String[]{"eventos","inventario","entradas"})env.withProperty("TY_SECURITY_"+target.toUpperCase()+"_SECRET",java.util.UUID.randomUUID().toString());
   var tokens=new ServiceTokens(env,clock);
   String first=tokens.token("inventario");assertEquals(first,tokens.token("inventario"));assertEquals(1,issued.get());
   clockValue.set(clockValue.get().plusSeconds(7200));assertNotEquals(first,tokens.token("inventario"));assertEquals(2,issued.get());
   var client=tokens.feign(ar.edu.unicen.grupo8.comprasservice.feign.FeignFixtures.Probe.class,"inventario","http://127.0.0.1:"+server.getAddress().getPort());
   assertThrows(TechnicalAuthFailure.class,()->client.write());
   assertEquals(1,business.get()); // no automatic POST replay
  }finally{server.stop(0);}
 }
}
