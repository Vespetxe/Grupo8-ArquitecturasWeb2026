package ar.edu.unicen.grupo8.comprasservice.security;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.bulkhead.Bulkhead;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class OAuthResilienceTest {
 static final String[] TARGETS={"eventos","inventario","entradas"};
 final AtomicReference<Instant> now=new AtomicReference<>(Instant.now());
 final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now.get();}};
 final AtomicInteger requests=new AtomicInteger(),writes=new AtomicInteger(),status=new AtomicInteger(200),ttl=new AtomicInteger(120),active=new AtomicInteger(),maxActive=new AtomicInteger();
 final AtomicBoolean hold=new AtomicBoolean(),malformed=new AtomicBoolean();final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
 HttpServer server;ExecutorService workers;ServiceTokens tokens;String url;
 @BeforeEach void start()throws Exception{workers=Executors.newCachedThreadPool();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(workers);server.createContext("/oauth2/token",this::grant);server.createContext("/write",x->{writes.incrementAndGet();x.getRequestBody().readAllBytes();x.sendResponseHeaders(204,-1);x.close();});server.start();url="http://127.0.0.1:"+server.getAddress().getPort();var env=new MockEnvironment().withProperty("TY_SECURITY_TOKEN_URL",url+"/oauth2/token").withProperty("ticketya.resilience.oauth.window","2").withProperty("ticketya.resilience.oauth.minimum","2");for(String target:TARGETS)env.setProperty("TY_SECURITY_"+target.toUpperCase(Locale.ROOT)+"_SECRET","fixture-"+target+"x".repeat(64));tokens=new ServiceTokens(env,clock);}
 void grant(HttpExchange x)throws java.io.IOException{int n=requests.incrementAndGet();int a=active.incrementAndGet();maxActive.accumulateAndGet(a,Math::max);try{x.getRequestBody().readAllBytes();if(hold.get()){entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}int code=status.get();String body=malformed.get()?"{}":code==200?"{\"access_token\":\"fixture-token-"+n+"\",\"token_type\":\"Bearer\",\"expires_in\":"+ttl.get()+"}":"{\"error\":\""+(code==401||code==403?"invalid_client":"temporarily_unavailable")+"\"}";byte[] b=body.getBytes();x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(code,b.length);x.getResponseBody().write(b);}finally{active.decrementAndGet();x.close();}}
 @AfterEach void stop()throws Exception{release.countDown();server.stop(0);workers.shutdownNow();tokens.closeTransport();}
 @SuppressWarnings("unchecked") CircuitBreaker circuit(String target){return ((Map<String,CircuitBreaker>)ReflectionTestUtils.getField(tokens,"tokenCircuits")).get(target);}
 @Test void validCachedTokenSurvivesIdentityOutageAndOpenCircuit(){String first=tokens.token(TARGETS[0]);status.set(503);circuit(TARGETS[0]).transitionToOpenState();assertEquals(first,tokens.token(TARGETS[0]));assertEquals(1,requests.get());}
 @Test void expiredTokenIsNotUsedAndOpenCircuitDoesNotSendBusinessWrite(){tokens.token(TARGETS[0]);now.set(now.get().plusSeconds(180));status.set(503);for(int i=0;i<2;i++)assertThrows(TechnicalAuthFailure.class,()->tokens.token(TARGETS[0]));assertEquals(CircuitBreaker.State.OPEN,circuit(TARGETS[0]).getState());var error=assertThrows(TechnicalAuthFailure.class,()->tokens.feign(ar.edu.unicen.grupo8.comprasservice.feign.FeignFixtures.Probe.class,TARGETS[0],url).write());assertEquals(TechnicalAuthFailure.Reason.TOKEN_CIRCUIT_OPEN,error.reason());assertEquals(2,requests.get());assertEquals(0,writes.get());}
 @Test void halfOpenRenewsOnlyOnDemandAndClosesAfterRealRenewals(){status.set(503);for(int i=0;i<2;i++)assertThrows(TechnicalAuthFailure.class,()->tokens.token(TARGETS[0]));assertEquals(CircuitBreaker.State.OPEN,circuit(TARGETS[0]).getState());status.set(200);now.set(now.get().plusSeconds(11));ttl.set(1200);String fresh=tokens.token(TARGETS[0]);assertEquals(CircuitBreaker.State.HALF_OPEN,circuit(TARGETS[0]).getState());assertEquals(fresh,tokens.token(TARGETS[0]));assertEquals(3,requests.get());now.set(now.get().plusSeconds(1300));ttl.set(86400);assertNotEquals(fresh,tokens.token(TARGETS[0]));assertEquals(CircuitBreaker.State.CLOSED,circuit(TARGETS[0]).getState());assertEquals(4,requests.get());}
 @Test void concurrentRenewalsAreSingleFlightPerLink()throws Exception{hold.set(true);var futures=new ArrayList<Future<String>>();for(int i=0;i<8;i++)futures.add(workers.submit(()->tokens.token(TARGETS[0])));assertTrue(entered.await(2,TimeUnit.SECONDS));release.countDown();var values=new HashSet<String>();for(var future:futures)values.add(future.get(3,TimeUnit.SECONDS));assertEquals(1,values.size());assertEquals(1,requests.get());assertEquals(1,maxActive.get());}
 @Test void tokenBulkheadRejectsWithoutDispatchButCacheStillWorks(){String cached=tokens.token(TARGETS[0]);var bulk=(Bulkhead)ReflectionTestUtils.getField(tokens,"tokenBulkhead");assertTrue(bulk.tryAcquirePermission());assertTrue(bulk.tryAcquirePermission());try{assertEquals(cached,tokens.token(TARGETS[0]));now.set(now.get().plusSeconds(180));var failure=assertThrows(TechnicalAuthFailure.class,()->tokens.token(TARGETS[0]));assertEquals(TechnicalAuthFailure.Reason.TOKEN_SATURATED,failure.reason());assertEquals(1,requests.get());assertEquals(0,circuit(TARGETS[0]).getMetrics().getNumberOfFailedCalls());}finally{bulk.onComplete();bulk.onComplete();}}
 @ParameterizedTest @ValueSource(ints={401,403}) void credentialErrorsStayDeniedWithoutFalseSuccess(int code){status.set(code);for(int i=0;i<3;i++)assertThrows(TechnicalAuthFailure.class,()->tokens.token(TARGETS[0]));assertEquals(3,requests.get());assertEquals(0,circuit(TARGETS[0]).getMetrics().getNumberOfBufferedCalls());assertEquals(CircuitBreaker.State.CLOSED,circuit(TARGETS[0]).getState());assertEquals(0,writes.get());}
 @ParameterizedTest @ValueSource(ints={429,503}) void availabilityFailuresOpenOnlyAfterActualAttempts(int code){status.set(code);for(int i=0;i<2;i++)assertThrows(TechnicalAuthFailure.class,()->tokens.token(TARGETS[0]));assertEquals(TechnicalAuthFailure.Reason.TOKEN_CIRCUIT_OPEN,assertThrows(TechnicalAuthFailure.class,()->tokens.token(TARGETS[0])).reason());assertEquals(2,requests.get());}
 @Test void malformedGrantIsNotAnInventedToken(){malformed.set(true);for(int i=0;i<2;i++)assertThrows(TechnicalAuthFailure.class,()->tokens.token(TARGETS[0]));assertEquals(CircuitBreaker.State.OPEN,circuit(TARGETS[0]).getState());assertEquals(0,writes.get());}

 @Test void cachedOtherLinkDoesNotWaitBehindBlockedRenewal()throws Exception{String cached=tokens.token(TARGETS[1]);hold.set(true);var pending=workers.submit(()->tokens.token(TARGETS[0]));assertTrue(entered.await(2,TimeUnit.SECONDS));long start=System.nanoTime();assertEquals(cached,tokens.token(TARGETS[1]));assertTrue((System.nanoTime()-start)/1_000_000<300);release.countDown();assertNotNull(pending.get(3,TimeUnit.SECONDS));assertEquals(2,requests.get());}

}
