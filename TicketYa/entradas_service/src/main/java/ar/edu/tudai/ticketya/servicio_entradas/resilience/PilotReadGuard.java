package ar.edu.tudai.ticketya.servicio_entradas.resilience;

import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.circuitbreaker.*;
import org.springframework.core.env.Environment;
import org.springframework.web.client.*;
import java.time.*;
import java.util.function.Supplier;
import ar.edu.tudai.ticketya.servicio_entradas.timing.RequestBudget;

/** One instance per selected read link. No fallback, retry, thread switch or business identity. */
public final class PilotReadGuard {
    private final CircuitBreaker circuit;
    private final Bulkhead bulkhead;
    private final String dependency, operation;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PilotReadGuard.class);
    public static final class Unavailable extends RestClientException {
        private final String category;
        private Unavailable(String category) { super("Read dependency unavailable: " + category); this.category=category; }
        public String category() { return category; }
    }
    public PilotReadGuard(Environment env) { this(env, Clock.systemUTC()); }
    PilotReadGuard(Environment env, Clock clock) {
        dependency="compras"; operation="order-read";
        int window=number(env,"window",20,2,1000);
        int minimum=number(env,"minimum",10,1,window);
        int threshold=number(env,"failure-percent",50,1,100);
        int open=number(env,"open-ms",10000,1,300000);
        int probes=number(env,"half-open-calls",2,1,8);
        int concurrent=number(env,"concurrent",8,1,64);
        circuit=CircuitBreaker.of("entradas-compras-read",CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(window).minimumNumberOfCalls(minimum).failureRateThreshold(threshold)
            .waitDurationInOpenState(Duration.ofMillis(open)).permittedNumberOfCallsInHalfOpenState(probes)
            .automaticTransitionFromOpenToHalfOpenEnabled(false).clock(clock)
            .ignoreException(failure -> !technical(failure)).recordException(PilotReadGuard::technical)
            .build());
        bulkhead=Bulkhead.of(circuit.getName(),BulkheadConfig.custom().maxConcurrentCalls(concurrent).maxWaitDuration(Duration.ZERO).build());
        circuit.getEventPublisher().onStateTransition(event -> log("CIRCUIT_TRANSITION","NONE",0));
    }
    private static int number(Environment env,String key,int fallback,int min,int max) {
        int value=env.getProperty("ticketya.resilience.read."+key,Integer.class,fallback);
        if(value<min||value>max) throw new IllegalStateException("Invalid read resilience configuration");
        return value;
    }
    static boolean technical(Throwable failure) {
        if(failure instanceof ar.edu.tudai.ticketya.servicio_entradas.security.TechnicalAuthFailure || failure instanceof RequestBudget.Exhausted) return false;
        if(failure instanceof RestClientResponseException response) return response.getStatusCode().value()==429 || response.getStatusCode().value()>=500;
        if (failure instanceof ar.edu.tudai.ticketya.servicio_entradas.integration.OrdersUnavailableException) return true;
        return failure instanceof RestClientException;
    }
    public <T> T execute(Supplier<T> read) {
        long start=System.nanoTime(); String category="NONE";
        try {
            // Reject an expired request before touching either permission or the HTTP/token client.
            RequestBudget.cap(1,2000);
            return bulkhead.executeSupplier(() -> circuit.executeSupplier(read));
        } catch(BulkheadFullException full) {
            category="BULKHEAD_FULL"; throw new Unavailable(category);
        } catch(CallNotPermittedException open) {
            category="CIRCUIT_OPEN"; throw new Unavailable(category);
        } catch(RuntimeException failure) {
            category=category(failure);
            throw failure;
        } finally {
            log("DEPENDENCY_READ",category,(System.nanoTime()-start)/1_000_000L);
        }
    }
    private static String category(Throwable failure) {
        if(failure instanceof RequestBudget.Exhausted) return "BUDGET_EXHAUSTED";
        if(failure instanceof ar.edu.tudai.ticketya.servicio_entradas.security.TechnicalAuthFailure) return "TECHNICAL_AUTH";
        if(failure instanceof RestClientResponseException response) return response.getStatusCode().value()==429?"RATE_LIMITED":response.getStatusCode().value()>=500?"UPSTREAM_5XX":"IGNORED";
        if(failure instanceof ar.edu.tudai.ticketya.servicio_entradas.integration.OrdersUnavailableException) return "INVALID_RESPONSE";
        if(failure instanceof ResourceAccessException) {
            Throwable cause=failure;
            for(int i=0;i<8&&cause!=null;i++,cause=cause.getCause())
                if(cause instanceof java.net.SocketTimeoutException || cause.getClass().getSimpleName().contains("Timeout")) return "TIMEOUT";
            return "CONNECTION";
        }
        return technical(failure)?"INVALID_RESPONSE":"IGNORED";
    }
    private void log(String event,String category,long duration) {
        var entry=LOG.atInfo().addKeyValue("eventType",event).addKeyValue("dependency",dependency)
            .addKeyValue("operation",operation).addKeyValue("circuit",circuit.getName())
            .addKeyValue("circuitState",circuit.getState().name()).addKeyValue("durationMs",duration).addKeyValue("errorCategory",category);
        var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            var state=(ar.edu.tudai.ticketya.servicio_entradas.observability.HttpLogState)servlet.getRequest().getAttribute(ar.edu.tudai.ticketya.servicio_entradas.observability.HttpLogState.ATTRIBUTE);
            if(state!=null) entry.addKeyValue("correlationId",state.correlationId());
        }
        // The existing tracing scope/MDC supplies traceId/spanId; never copy headers or JWT.
        entry.log("Read dependency resilience");
    }
    CircuitBreaker circuit() { return circuit; }
    Bulkhead bulkhead() { return bulkhead; }
}
