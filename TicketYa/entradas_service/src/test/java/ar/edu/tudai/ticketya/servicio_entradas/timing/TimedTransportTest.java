package ar.edu.tudai.ticketya.servicio_entradas.timing;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import org.springframework.web.client.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class TimedTransportTest {
 HttpServer server;ExecutorService workers;String url;
 @BeforeEach void start()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);workers=Executors.newCachedThreadPool();server.setExecutor(workers);server.start();url="http://127.0.0.1:"+server.getAddress().getPort();}
 @AfterEach void stop(){server.stop(0);workers.shutdownNow();RequestContextHolder.resetRequestAttributes();}
 void respond(com.sun.net.httpserver.HttpExchange x)throws java.io.IOException{byte[] body="{}".getBytes();x.sendResponseHeaders(200,body.length);x.getResponseBody().write(body);x.close();}
 @Test void configuredReadTimeoutIsEffectiveAndDoesNotRetry()throws Exception{
  var count=new AtomicInteger();server.createContext("/",x->{count.incrementAndGet();try{Thread.sleep(400);respond(x);}catch(Exception ignored){x.close();}});
  try(var transport=new TimedTransport(new MockEnvironment().withProperty("ticketya.http.read-ms","50"))){
   var client=RestClient.builder().baseUrl(url).requestFactory(transport.factory(false)).requestInterceptor(transport.propagate()).build();
   long start=System.nanoTime();assertThrows(RestClientException.class,()->client.get().retrieve().body(String.class));assertTrue((System.nanoTime()-start)/1_000_000<3000);assertEquals(1,count.get());assertEquals(50,transport.readMs);
  }
 }
 @Test void sharedPoolAcquisitionIsBoundedUnderSlowDependency()throws Exception{
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  server.createContext("/",x->{entered.countDown();try{release.await(3,TimeUnit.SECONDS);respond(x);}catch(Exception ignored){x.close();}});
  try(var transport=new TimedTransport(new MockEnvironment().withProperty("ticketya.http.pool-size","1").withProperty("ticketya.http.per-route","1").withProperty("ticketya.http.acquire-ms","50"))){
   var client=RestClient.builder().baseUrl(url).requestFactory(transport.factory(false)).build();
   var first=workers.submit(()->client.get().retrieve().body(String.class));assertTrue(entered.await(3,TimeUnit.SECONDS));
   long start=System.nanoTime();assertThrows(RestClientException.class,()->client.get().retrieve().body(String.class));assertTrue((System.nanoTime()-start)/1_000_000<1500);assertEquals(1,transport.leased());release.countDown();assertEquals("{}",first.get(3,TimeUnit.SECONDS));
  }finally{release.countDown();}
 }
 @Test void executedWriteWithLostResponseIsNeverReplayed()throws Exception{
  var count=new AtomicInteger();server.createContext("/",x->{x.getRequestBody().readAllBytes();count.incrementAndGet();x.close();});
  try(var transport=new TimedTransport(new MockEnvironment())){
   var client=RestClient.builder().baseUrl(url).requestFactory(transport.factory(false)).build();
   assertThrows(RestClientException.class,()->client.post().body("same-operation").retrieve().toBodilessEntity());assertEquals(1,count.get());
  }
 }
 @Test void exhaustedBudgetPreventsDispatchAndCannotBeExtendedByHeader()throws Exception{
  var request=new MockHttpServletRequest();request.addHeader(RequestBudget.HEADER,"999999");RequestBudget.start(request,12000);RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));assertTrue(RequestBudget.remaining()<=12000);
  RequestBudget.testDeadline(request,100_000_000L,()->0L);assertEquals(100,RequestBudget.remaining());
  try(var transport=new TimedTransport(new MockEnvironment())){var client=RestClient.builder().baseUrl(url).requestFactory(transport.factory(false)).build();assertThrows(RestClientException.class,()->client.post().retrieve().toBodilessEntity());}
 }
 @Test void slowTokenRenewalIsBoundedAndDoesNotReplayBusinessWrite()throws Exception{
  var tokenCalls=new AtomicInteger();var writes=new AtomicInteger();
  server.createContext("/oauth2/token",x->{tokenCalls.incrementAndGet();try{Thread.sleep(400);byte[] b="{\"access_token\":\"fixture\",\"token_type\":\"Bearer\",\"expires_in\":120}".getBytes();x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);}catch(Exception ignored){}finally{x.close();}});
  server.createContext("/write",x->{writes.incrementAndGet();respond(x);});
  var env=new MockEnvironment().withProperty("TY_SECURITY_TOKEN_URL",url+"/oauth2/token").withProperty("ticketya.http.token-ms","50");
  for(String name:new String[]{"EVENTOS","INVENTARIO","ENTRADAS","COMPRAS"})env.setProperty("TY_SECURITY_"+name+"_SECRET","x".repeat(64));
  var tokens=new ar.edu.tudai.ticketya.servicio_entradas.security.ServiceTokens(env);
  try{long start=System.nanoTime();assertThrows(ar.edu.tudai.ticketya.servicio_entradas.security.TechnicalAuthFailure.class,()->tokens.feign(ar.edu.tudai.ticketya.servicio_entradas.feign.FeignFixtures.Probe.class,"compras",url).write());assertTrue((System.nanoTime()-start)/1_000_000<3000);assertEquals(1,tokenCalls.get());assertEquals(0,writes.get());}finally{tokens.closeTransport();}
 }
 @Test void connectionHandshakeTimeoutIsEffective()throws Exception{
  try(var socket=new java.net.ServerSocket(0);var transport=new TimedTransport(new MockEnvironment().withProperty("ticketya.http.connect-ms","75"))){
   var accepted=workers.submit(()->{try(var connection=socket.accept()){Thread.sleep(500);}catch(Exception ignored){}});
   var client=RestClient.builder().requestFactory(transport.factory(false)).build();long start=System.nanoTime();
   assertThrows(RestClientException.class,()->client.get().uri("https://127.0.0.1:"+socket.getLocalPort()).retrieve().body(String.class));
   long elapsed=(System.nanoTime()-start)/1_000_000;assertTrue(elapsed>=40&&elapsed<2000);accepted.get(3,TimeUnit.SECONDS);
  }
 }
 @Test void connectionFailureUsesConfiguredFactoryWithoutRetry()throws Exception{
  int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
  try(var transport=new TimedTransport(new MockEnvironment().withProperty("ticketya.http.connect-ms","75"))){
   assertEquals(75,transport.connectMs);var client=RestClient.builder().requestFactory(transport.factory(false)).build();
   assertThrows(RestClientException.class,()->client.get().uri("http://127.0.0.1:"+port).retrieve().body(String.class));
  }
 }
}
