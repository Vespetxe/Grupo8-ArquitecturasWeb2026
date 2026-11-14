package ar.edu.ticketya.gateway.observability;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/** Outside Spring Security: also observes authentication/authorization failures. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationWebFilter implements WebFilter {
    private final io.micrometer.tracing.Tracer tracer;
    public CorrelationWebFilter(){this(null);}
    @org.springframework.beans.factory.annotation.Autowired
    public CorrelationWebFilter(io.micrometer.tracing.Tracer tracer){this.tracer=tracer;}
    @Override public Mono<Void> filter(ServerWebExchange original, WebFilterChain chain) {
        return Mono.defer(() -> {
            String id = CorrelationIds.fresh(); // Public ingress never trusts a supplied ID.
            var state = new HttpLogState(id, original.getRequest().getMethod().name(),
                    original.getRequest().getPath().pathWithinApplication().value());
            state.trace(tracer);
            var exchange = original.mutate().request(original.getRequest().mutate()
                    .headers(headers -> headers.set(CorrelationIds.HEADER, id)).build()).build();
            exchange.getAttributes().put(HttpLogState.ATTRIBUTE, state);
            var response = exchange.getResponse();
            response.getHeaders().set(CorrelationIds.HEADER, id);
            var failed = new java.util.concurrent.atomic.AtomicBoolean();
            response.beforeCommit(() -> {
                // An upstream response cannot replace the ingress correlation ID.
                response.getHeaders().set(CorrelationIds.HEADER, id);
                if (failed.get()) state.finish(status(exchange), HttpLogState.Completion.ERROR);
                return Mono.empty();
            });
            return Mono.defer(() -> chain.filter(exchange))
                    .doOnError(error -> { failed.set(true); state.failure(error); })
                    .doFinally(signal -> {
                        if (signal == SignalType.CANCEL) {
                            state.finish(response.isCommitted() ? status(exchange) : 0, HttpLogState.Completion.CANCELLED);
                        } else if (signal == SignalType.ON_COMPLETE) {
                            state.finish(status(exchange), HttpLogState.Completion.COMPLETED);
                        } else if (signal == SignalType.ON_ERROR && response.isCommitted()) {
                            state.finish(status(exchange), HttpLogState.Completion.ERROR);
                        }
                        // For an uncommitted error, outer WebExceptionHandlers determine the
                        // actual HTTP status. beforeCommit records that status, exactly once.
                    }).contextWrite(context -> context.put(HttpLogState.class, state));
        });
    }

    private static int status(ServerWebExchange exchange) {
        var status = exchange.getResponse().getStatusCode();
        return status == null ? 200 : status.value();
    }
}
