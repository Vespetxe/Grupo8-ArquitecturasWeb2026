package grupo22.eventocatalogo_service.resilience;

import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.circuitbreaker.*;
import org.springframework.core.env.Environment;
import org.springframework.web.client.*;
import java.time.*;
import java.util.function.Supplier;
import grupo22.eventocatalogo_service.timing.RequestBudget;

/** Reused pilot guard, one instance per selected HTTP family. Never retries or invents business data. */
public final class PilotReadGuard {
    public enum Family {
        HISTORY("compras","purchase-history","eventos-compras-history","purchase-history"),
        INVENTORY_INIT("inventario","inventory-init","eventos-inventario-init","inventory-init"),
        INVENTORY_LIFECYCLE("inventario","inventory-lifecycle","eventos-inventario-lifecycle","inventory-lifecycle"),
        EMISSION_BLOCK("entradas","emission-block","eventos-entradas-block","emission-block"),
        CANCELLATION("compras","event-cancel","eventos-compras-cancel","event-cancel");
        final String dependency,operation,circuit,settings;
        Family(String dependency,String operation,String circuit,String settings){this.dependency=dependency;this.operation=operation;this.circuit=circuit;this.settings=settings;}
    }
    private final CircuitBreaker circuit;
    private final Bulkhead bulkhead;
    private final String dependency, operation;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PilotReadGuard.class);
    public static final class Unavailable extends RestClientException {
        private final String category;
        private Unavailable(String category) { super("Dependency not dispatched: " + category); this.category=category; }
        public String category() { return category; }
        /** Local admission rejection: the protected HTTP supplier was never called. */
        public boolean dispatched() { return false; }
    }
    public PilotReadGuard(Environment env) { this(env, Clock.systemUTC()); }
    public PilotReadGuard(Environment env, Family family) { this(env,Clock.systemUTC(),family); }
    PilotReadGuard(Environment env, Clock clock) { this(env,clock,Family.INVENTORY_INIT); }
    PilotReadGuard(Environment env, Clock clock,Family family) {
        dependency=family.dependency; operation=family.operation;
        int window=number(env,family,"window",20,2,1000);
        int minimum=number(env,family,"minimum",10,1,window);
        int threshold=number(env,family,"failure-percent",50,1,100);
        int open=number(env,family,"open-ms",10000,1,300000);
        int probes=number(env,family,"half-open-calls",2,1,8);
        int concurrent=number(env,family,"concurrent",8,1,64);
        circuit=CircuitBreaker.of(family.circuit,CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(window).minimumNumberOfCalls(minimum).failureRateThreshold(threshold)
            .waitDurationInOpenState(Duration.ofMillis(open)).permittedNumberOfCallsInHalfOpenState(probes)
            .automaticTransitionFromOpenToHalfOpenEnabled(false).clock(clock)
            .ignoreException(failure -> !technical(failure)).recordException(PilotReadGuard::technical)
            .build());
        bulkhead=Bulkhead.of(circuit.getName(),BulkheadConfig.custom().maxConcurrentCalls(concurrent).maxWaitDuration(Duration.ZERO).build());
        circuit.getEventPublisher().onStateTransition(event -> log("CIRCUIT_TRANSITION","NONE",0));
    }
    private static int number(Environment env,Family family,String key,int fallback,int min,int max) {
        int inherited=env.getProperty("ticketya.resilience.read."+key,Integer.class,fallback);
        int value=env.getProperty("ticketya.resilience."+family.settings+"."+key,Integer.class,inherited);
        if(value<min||value>max) throw new IllegalStateException("Invalid read resilience configuration");
        return value;
    }
    static boolean technical(Throwable failure) {
        if(failure instanceof grupo22.eventocatalogo_service.security.TechnicalAuthFailure || failure instanceof RequestBudget.Exhausted) return false;
        if(failure instanceof RestClientResponseException response) return response.getStatusCode().value()==429 || response.getStatusCode().value()>=500;
        if(failure instanceof org.springframework.web.server.ResponseStatusException status) return status.getStatusCode().is5xxServerError();
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
            log("DEPENDENCY_COMMAND",category,(System.nanoTime()-start)/1_000_000L);
        }
    }
    private static String category(Throwable failure) {
        if(failure instanceof RequestBudget.Exhausted) return "BUDGET_EXHAUSTED";
        if(failure instanceof grupo22.eventocatalogo_service.security.TechnicalAuthFailure) return "TECHNICAL_AUTH";
        if(failure instanceof RestClientResponseException response) return response.getStatusCode().value()==429?"RATE_LIMITED":response.getStatusCode().value()>=500?"UPSTREAM_5XX":"IGNORED";
        if(failure instanceof org.springframework.web.server.ResponseStatusException) return "INVALID_RESPONSE";
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
            var state=(grupo22.eventocatalogo_service.observability.HttpLogState)servlet.getRequest().getAttribute(grupo22.eventocatalogo_service.observability.HttpLogState.ATTRIBUTE);
            if(state!=null) entry.addKeyValue("correlationId",state.correlationId());
        }
        // The existing tracing scope/MDC supplies traceId/spanId; never copy headers or JWT.
        entry.log("HTTP dependency resilience");
    }
    CircuitBreaker circuit() { return circuit; }
    Bulkhead bulkhead() { return bulkhead; }
}
