package ar.edu.tudai.ticketya.servicio_entradas.resilience;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.*;
import org.springframework.http.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class PilotReadGuardTest {
    static class TestClock extends Clock {
        Instant now=Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
        void advance(){now=now.plusSeconds(11);}
    }
    final TestClock clock=new TestClock();
    final PilotReadGuard guard=new PilotReadGuard(new MockEnvironment(),clock);
    RestClientResponseException status(int code){return new RestClientResponseException("sentinel-password",code,"",HttpHeaders.EMPTY,"sentinel-token".getBytes(),null);}
    void open(){for(int i=0;i<10;i++)assertThrows(RestClientException.class,()->guard.execute(()->{throw status(503);}));assertEquals(CircuitBreaker.State.OPEN,guard.circuit().getState());}
    @AfterEach void cleanup(){org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();org.slf4j.MDC.clear();}
    @Test void defaultsAreEffective(){var c=guard.circuit().getCircuitBreakerConfig();assertEquals(20,c.getSlidingWindowSize());assertEquals(10,c.getMinimumNumberOfCalls());assertEquals(50,c.getFailureRateThreshold());assertEquals(2,c.getPermittedNumberOfCallsInHalfOpenState());assertEquals(10000,c.getWaitIntervalFunctionInOpenState().apply(1));assertEquals(8,guard.bulkhead().getBulkheadConfig().getMaxConcurrentCalls());assertEquals(Duration.ZERO,guard.bulkhead().getBulkheadConfig().getMaxWaitDuration());assertFalse(c.isAutomaticTransitionFromOpenToHalfOpenEnabled());}
    @ParameterizedTest @ValueSource(ints={429,500,502,503,504})
    void relevantTechnicalStatusesOpen(int status){for(int i=0;i<10;i++)assertThrows(RestClientResponseException.class,()->guard.execute(()->{throw status(status);}));assertEquals(CircuitBreaker.State.OPEN,guard.circuit().getState());}
    @ParameterizedTest @ValueSource(ints={400,401,403,404,409,422})
    void ignoredStatusesAreNeitherFailuresNorFakeSuccess(int status){for(int i=0;i<12;i++)assertThrows(RestClientResponseException.class,()->guard.execute(()->{throw status(status);}));assertEquals(0,guard.circuit().getMetrics().getNumberOfBufferedCalls());assertEquals(0,guard.circuit().getMetrics().getNumberOfSuccessfulCalls());assertEquals(CircuitBreaker.State.CLOSED,guard.circuit().getState());}
    @Test void technicalAuthenticationFailureIsPreservedAndIgnored(){var failure=new ar.edu.tudai.ticketya.servicio_entradas.security.TechnicalAuthFailure();assertSame(failure,assertThrows(ar.edu.tudai.ticketya.servicio_entradas.security.TechnicalAuthFailure.class,()->guard.execute(()->{throw failure;})));assertEquals(0,guard.circuit().getMetrics().getNumberOfBufferedCalls());}
    @Test void commercialRejectionIsNotAFakeSuccess(){assertThrows(IllegalArgumentException.class,()->guard.execute(()->{throw new IllegalArgumentException("Business rejection");}));assertEquals(0,guard.circuit().getMetrics().getNumberOfBufferedCalls());}
    @Test void fiftyPercentWithTenCallsOpensAndDoesNotDispatchOrInventValue(){var dispatch=new AtomicInteger();for(int i=0;i<5;i++)assertEquals("actual",guard.execute(()->{dispatch.incrementAndGet();return "actual";}));for(int i=0;i<5;i++)assertThrows(RestClientException.class,()->guard.execute(()->{dispatch.incrementAndGet();throw status(503);}));assertEquals(CircuitBreaker.State.OPEN,guard.circuit().getState());assertEquals("CIRCUIT_OPEN",assertThrows(PilotReadGuard.Unavailable.class,()->guard.execute(()->{dispatch.incrementAndGet();return "invented";})).category());assertEquals(10,dispatch.get());}
    @Test void halfOpenAdmitsTwoRealProbesAndClosesAfterRecovery()throws Exception{
        open();clock.advance();var entered=new CountDownLatch(2);var release=new CountDownLatch(1);var workers=Executors.newFixedThreadPool(2);
        try{var read=(java.util.function.Supplier<String>)()->{entered.countDown();try{assertTrue(release.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}return "actual";};var one=workers.submit(()->guard.execute(read));var two=workers.submit(()->guard.execute(read));assertTrue(entered.await(3,TimeUnit.SECONDS));assertEquals(CircuitBreaker.State.HALF_OPEN,guard.circuit().getState());assertThrows(PilotReadGuard.Unavailable.class,()->guard.execute(()->"must-not-run"));release.countDown();assertEquals("actual",one.get(3,TimeUnit.SECONDS));assertEquals("actual",two.get(3,TimeUnit.SECONDS));assertEquals(CircuitBreaker.State.CLOSED,guard.circuit().getState());}finally{release.countDown();workers.shutdownNow();}
    }
    @Test void halfOpenFailureReopensWithoutBackgroundProbe(){open();clock.advance();assertThrows(RestClientException.class,()->guard.execute(()->{throw status(503);}));assertThrows(RestClientException.class,()->guard.execute(()->{throw status(503);}));assertEquals(CircuitBreaker.State.OPEN,guard.circuit().getState());}
    @Test void bulkheadEightCallsHasNoQueueAndRecoversWithoutStatisticsPollution()throws Exception{
        var entered=new CountDownLatch(8);var release=new CountDownLatch(1);var workers=Executors.newFixedThreadPool(8);var results=new java.util.ArrayList<Future<String>>();
        try{for(int i=0;i<8;i++)results.add(workers.submit(()->guard.execute(()->{entered.countDown();try{assertTrue(release.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}return "real";})));assertTrue(entered.await(3,TimeUnit.SECONDS));assertEquals(0,guard.bulkhead().getMetrics().getAvailableConcurrentCalls());long start=System.nanoTime();assertEquals("BULKHEAD_FULL",assertThrows(PilotReadGuard.Unavailable.class,()->guard.execute(()->"fake")).category());assertTrue((System.nanoTime()-start)/1_000_000<500);assertEquals(0,guard.circuit().getMetrics().getNumberOfFailedCalls());release.countDown();for(var result:results)assertEquals("real",result.get(3,TimeUnit.SECONDS));assertEquals(8,guard.bulkhead().getMetrics().getAvailableConcurrentCalls());assertEquals("recovered",guard.execute(()->"recovered"));}finally{release.countDown();workers.shutdownNow();}
    }
    @Test void circuitInstancesHaveIndependentStates(){open();var other=new PilotReadGuard(new MockEnvironment(),clock);assertEquals("actual",other.execute(()->"actual"));assertEquals(CircuitBreaker.State.CLOSED,other.circuit().getState());assertEquals(0,other.circuit().getMetrics().getNumberOfFailedCalls());}
    @Test void expiredBudgetDoesNotAcquirePermissionsOrDispatch(){var request=new org.springframework.mock.web.MockHttpServletRequest();ar.edu.tudai.ticketya.servicio_entradas.timing.RequestBudget.start(request,0);org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));var calls=new AtomicInteger();assertThrows(ar.edu.tudai.ticketya.servicio_entradas.timing.RequestBudget.Exhausted.class,()->guard.execute(()->calls.incrementAndGet()));assertEquals(0,calls.get());assertEquals(0,guard.circuit().getMetrics().getNumberOfBufferedCalls());assertEquals(8,guard.bulkhead().getMetrics().getAvailableConcurrentCalls());}
    @Test void invalidConfigFailsClosed(){assertThrows(IllegalStateException.class,()->new PilotReadGuard(new MockEnvironment().withProperty("ticketya.resilience.read.concurrent","0")));assertThrows(IllegalStateException.class,()->new PilotReadGuard(new MockEnvironment().withProperty("ticketya.resilience.read.minimum","21")));}
    @Test void transportTimeoutsAreRecordedButNeverRetried(){var calls=new AtomicInteger();for(int i=0;i<10;i++)assertThrows(ResourceAccessException.class,()->guard.execute(()->{calls.incrementAndGet();throw new ResourceAccessException("sentinel-token",new java.net.SocketTimeoutException());}));assertEquals(10,calls.get());assertEquals(CircuitBreaker.State.OPEN,guard.circuit().getState());}
    @Test void controlledLogsRetainCorrelationAndNeverUseRemoteMessages(){
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(PilotReadGuard.class);var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();logger.addAppender(appender);
        var request=new org.springframework.mock.web.MockHttpServletRequest();var cid="79bfec5d-a367-4d6f-a845-e116db8d30a3";request.setAttribute(ar.edu.tudai.ticketya.servicio_entradas.observability.HttpLogState.ATTRIBUTE,new ar.edu.tudai.ticketya.servicio_entradas.observability.HttpLogState(cid,"GET","/"));org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        try{assertThrows(RestClientResponseException.class,()->guard.execute(()->{throw status(503);}));var event=appender.list.getLast();var fields=event.getKeyValuePairs().stream().collect(java.util.stream.Collectors.toMap(v->v.key,v->v.value));assertEquals(cid,fields.get("correlationId"));assertEquals("UPSTREAM_5XX",fields.get("errorCategory"));assertNotNull(fields.get("circuit"));assertNotNull(fields.get("durationMs"));assertNull(event.getThrowableProxy());assertFalse(event.getFormattedMessage().contains("sentinel"));assertFalse(fields.toString().contains("sentinel"));}finally{logger.detachAppender(appender);appender.stop();}
    }
}
