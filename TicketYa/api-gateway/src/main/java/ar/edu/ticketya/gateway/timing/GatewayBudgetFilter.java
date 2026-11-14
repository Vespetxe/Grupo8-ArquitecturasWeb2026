package ar.edu.ticketya.gateway.timing;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
/** Public input cannot increase an internal budget. Timeout does not roll back upstream. */
@org.springframework.stereotype.Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE+1)
public class GatewayBudgetFilter implements WebFilter {
 private final long millis;
 public GatewayBudgetFilter(org.springframework.core.env.Environment env){millis=env.getProperty("ticketya.gateway.budget-ms",Long.class,15000L);if(millis<50||millis>120000)throw new IllegalStateException("Invalid Gateway budget");}
 @Override public Mono<Void> filter(ServerWebExchange original,WebFilterChain chain){
  return Mono.defer(()->{
   var exchange=original.mutate().request(original.getRequest().mutate().headers(h->h.set("X-TicketYa-Remaining-Ms",String.valueOf(Math.max(1,millis-2000)))).build()).build();
   return chain.filter(exchange).timeout(java.time.Duration.ofMillis(millis),Mono.defer(()->{
    var state=(ar.edu.ticketya.gateway.observability.HttpLogState)exchange.getAttribute(ar.edu.ticketya.gateway.observability.HttpLogState.ATTRIBUTE);if(state!=null)state.timeout();
    return Mono.error(new ResponseStatusException(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,"Gateway budget exhausted; reconcile original operation"));
   }));
  });
 }
}
