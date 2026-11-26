package ar.edu.unicen.grupo8.comprasservice.observability;
import ar.edu.unicen.grupo8.comprasservice.security.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.net.*;import java.time.*;import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.test.context.ContextConfiguration(initializers=ar.edu.unicen.grupo8.comprasservice.SecurityTestKeys.class)
@org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
class TechnicalTracingTest {
 @org.springframework.beans.factory.annotation.Autowired org.springframework.web.client.RestClient.Builder builder;
 @org.springframework.beans.factory.annotation.Autowired io.micrometer.tracing.Tracer tracer;
 @org.springframework.beans.factory.annotation.Autowired io.micrometer.observation.ObservationRegistry registry;
 @Test void businessReadTimeoutUsesTwoSecondsWithoutReplay()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  var executor=java.util.concurrent.Executors.newCachedThreadPool();server.setExecutor(executor);
  var calls=new AtomicInteger();
  server.createContext("/oauth2/token",e->{
   e.getRequestBody().readAllBytes();
   byte[] data="{\"access_token\":\"timeout-test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}".getBytes();
   e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,data.length);e.getResponseBody().write(data);e.close();
  });
  server.createContext("/slow",e->{calls.incrementAndGet();try{Thread.sleep(5000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}finally{e.close();}});
  server.start();
  try{
   var env=new MockEnvironment().withProperty("TY_SECURITY_TOKEN_URL","http://127.0.0.1:"+server.getAddress().getPort()+"/oauth2/token");
   for(String target:new String[]{"eventos","inventario","entradas"})env.withProperty("TY_SECURITY_"+target.toUpperCase()+"_SECRET",java.util.UUID.randomUUID().toString());
   var tokens=new ServiceTokens(env,Clock.systemUTC(),builder);
   var client=ar.edu.unicen.grupo8.comprasservice.feign.FeignSupport.observedTarget(ar.edu.unicen.grupo8.comprasservice.feign.FeignFixtures.Probe.class,tokens.transport("inventario"),"http://127.0.0.1:"+server.getAddress().getPort(),registry);
   long started=System.nanoTime();
   assertThrows(org.springframework.web.client.ResourceAccessException.class,()->client.slow());
   long elapsed=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
   assertTrue(elapsed>=1500&&elapsed<5000,"Read timeout outside configured two-second window");
   assertEquals(1,calls.get());assertNull(tracer.currentSpan());
  }finally{server.stop(0);executor.shutdownNow();}
 }
 @Test void cachesRenewsAndNeverReplaysBusinessWritesOnAuthenticationFailure()throws Exception{
  var clockValue=new AtomicReference<>(Instant.now());
  Clock clock=new Clock(){public java.time.ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(java.time.ZoneId z){return this;}public Instant instant(){return clockValue.get();}};
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  var parents=new java.util.concurrent.CopyOnWriteArrayList<String>();
  var correlations=new java.util.concurrent.CopyOnWriteArrayList<String>();
  var issued=new AtomicInteger();var business=new AtomicInteger();
  server.createContext("/oauth2/token",e->{parents.add(e.getRequestHeaders().getFirst("traceparent"));correlations.add(e.getRequestHeaders().getFirst(CorrelationIds.HEADER));String request=new String(e.getRequestBody().readAllBytes());assertTrue(request.contains("scope="));assertTrue(request.contains("reservations.read"));byte[] b=("{\"access_token\":\"test-token-"+issued.incrementAndGet()+"\",\"token_type\":\"Bearer\",\"expires_in\":3600}").getBytes();e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
  server.createContext("/write",e->{parents.add(e.getRequestHeaders().getFirst("traceparent"));correlations.add(e.getRequestHeaders().getFirst(CorrelationIds.HEADER));assertNotNull(e.getRequestHeaders().getFirst("Authorization"));business.incrementAndGet();e.getRequestBody().readAllBytes();e.sendResponseHeaders(401,-1);e.close();});server.start();
  var root=tracer.nextSpan().name("technical-test").start();
  var previous=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
  var request=new org.springframework.mock.web.MockHttpServletRequest();
  var state=new HttpLogState(CorrelationIds.fresh(),"POST","/pagos");request.setAttribute(HttpLogState.ATTRIBUTE,state);
  try(var spanScope=tracer.withSpan(root)){
   org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
   var env=new MockEnvironment().withProperty("TY_SECURITY_TOKEN_URL","http://127.0.0.1:"+server.getAddress().getPort()+"/oauth2/token");
   for(String target:new String[]{"eventos","inventario","entradas"})env.withProperty("TY_SECURITY_"+target.toUpperCase()+"_SECRET",java.util.UUID.randomUUID().toString());
   var tokens=new ServiceTokens(env,clock,builder);
   String first=tokens.token("inventario");assertEquals(first,tokens.token("inventario"));assertEquals(1,issued.get());
   clockValue.set(clockValue.get().plusSeconds(7200));assertNotEquals(first,tokens.token("inventario"));assertEquals(2,issued.get());clockValue.set(clockValue.get().minusSeconds(7200));
   var client=ar.edu.unicen.grupo8.comprasservice.feign.FeignSupport.observedTarget(ar.edu.unicen.grupo8.comprasservice.feign.FeignFixtures.Probe.class,tokens.transport("inventario"),"http://127.0.0.1:"+server.getAddress().getPort(),registry);
   assertThrows(TechnicalAuthFailure.class,()->client.write());
   assertEquals(1,business.get());
   tokens.token("inventario");assertEquals(3,issued.get());
   assertTrue(parents.stream().allMatch(p->p!=null&&p.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")),parents.toString());
   assertTrue(parents.stream().allMatch(p->p.split("-")[1].equals(root.context().traceId())));
   assertEquals(parents.size(),parents.stream().map(p->p.split("-")[2]).distinct().count());
   assertTrue(correlations.stream().allMatch(state.correlationId()::equals)); // no automatic POST replay
  }finally{org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(previous);root.end();server.stop(0);}
  assertNull(tracer.currentSpan());assertSame(previous,org.springframework.web.context.request.RequestContextHolder.getRequestAttributes());
 }
}
