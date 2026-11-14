package ar.edu.ticketya.gateway.timing;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayBudgetHttpTest {
 static com.sun.net.httpserver.HttpServer upstream;static java.util.concurrent.ExecutorService executor;
 static final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
 @org.springframework.beans.factory.annotation.Value("${local.server.port}") int port;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry registry)throws Exception{
  upstream=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);executor=java.util.concurrent.Executors.newCachedThreadPool();upstream.setExecutor(executor);
  upstream.createContext("/",x->{calls.incrementAndGet();try{if("slow".equals(x.getRequestURI().getRawQuery()))Thread.sleep(1500);byte[] b="[]".getBytes();x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);}catch(Exception ignored){}finally{x.close();}});upstream.start();
  registry.add("ticketya.eventos.url",()->"http://127.0.0.1:"+upstream.getAddress().getPort());registry.add("ticketya.gateway.budget-ms",()->"500");registry.add("spring.cloud.gateway.server.webflux.httpclient.response-timeout",()->"10s");
 }
 @AfterAll static void stop(){upstream.stop(0);executor.shutdownNow();}
 @Test void gatewayEnforcesOverallBudgetOnRealHttpWithoutRetry()throws Exception{
  var client=java.net.http.HttpClient.newHttpClient();
  var warm=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/v1/venues")).GET().build();client.send(warm,java.net.http.HttpResponse.BodyHandlers.ofString());
  int before=calls.get();var slow=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/v1/venues?slow")).timeout(java.time.Duration.ofSeconds(5)).header("X-TicketYa-Remaining-Ms","999999").GET().build();
  long start=System.nanoTime();var response=client.send(slow,java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(504,response.statusCode());assertTrue((System.nanoTime()-start)/1_000_000<1400);assertEquals(before+1,calls.get());assertTrue(response.headers().firstValue("X-Correlation-Id").isPresent());
 }
}
