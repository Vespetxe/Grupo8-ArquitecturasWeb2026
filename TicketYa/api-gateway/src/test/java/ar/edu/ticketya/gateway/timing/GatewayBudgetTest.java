package ar.edu.ticketya.gateway.timing;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
class GatewayBudgetTest {
 @Test void overallDeadlineIncludesSlowBodyAndCannotRetryAWrite(){
  var calls=new java.util.concurrent.atomic.AtomicInteger();var filter=new GatewayBudgetFilter(new MockEnvironment().withProperty("ticketya.gateway.budget-ms","50"));
  var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/pagos").header("X-TicketYa-Remaining-Ms","999999"));
  var failure=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->filter.filter(exchange,e->{calls.incrementAndGet();assertEquals("1",e.getRequest().getHeaders().getFirst("X-TicketYa-Remaining-Ms"));return Mono.delay(java.time.Duration.ofMillis(500)).then();}).block());
  assertEquals(504,failure.getStatusCode().value());assertEquals(1,calls.get());
 }
 @Test void fastRequestsKeepTheirResult(){var filter=new GatewayBudgetFilter(new MockEnvironment());var e=MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/venues"));filter.filter(e,x->Mono.empty()).block();assertNull(e.getResponse().getStatusCode());}
}
