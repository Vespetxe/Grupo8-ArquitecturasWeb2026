package ar.edu.ticketya.gateway.observability;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Installed only inside SecurityWebFilterChain, after JWT authentication. */
public class GatewayActorCaptureFilter implements WebFilter {
    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.deferContextual(context -> {
            HttpLogState state = context.getOrDefault(HttpLogState.class, null);
            return ReactiveSecurityContextHolder.getContext()
                    .doOnNext(security -> { if (state != null) state.authenticated(security.getAuthentication()); })
                    .then(Mono.defer(() -> chain.filter(exchange)));
        });
    }
}
