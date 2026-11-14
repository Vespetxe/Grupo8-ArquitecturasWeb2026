package ar.edu.ticketya.gateway.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.*;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.*;

class ReactiveLifecycleTest {
    @Test void contextSurvivesThreadHopAndDoesNotLeakOutsideTheRequest() throws Exception {
        try (var logs = new RequestLogCapture()) {
            var before = MDC.getCopyOfContextMap();
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/venues?password=sentinel-40"));
            new CorrelationWebFilter().filter(exchange, current -> Mono.delay(Duration.ofMillis(10))
                    .then(Mono.deferContextual(context -> {
                        var state = context.get(HttpLogState.class);
                        assertEquals(current.getRequest().getHeaders().getFirst(CorrelationIds.HEADER), state.correlationId());
                        return current.getResponse().setComplete();
                    }))).then(Mono.deferContextual(context -> {
                        assertFalse(context.hasKey(HttpLogState.class)); return Mono.empty();
                    })).block(Duration.ofSeconds(3));
            assertEquals(before, MDC.getCopyOfContextMap());
            String id = exchange.getResponse().getHeaders().getFirst(CorrelationIds.HEADER);
            assertEquals(200, logs.fields(logs.find(id)).get("httpStatus"));
        }
    }

    @Test void cancellationRecordsNoInventedHttpStatusAndExactlyOneEvent() throws Exception {
        try (var logs = new RequestLogCapture()) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/venues"));
            var subscription = new CorrelationWebFilter().filter(exchange, current -> Mono.never()).subscribe();
            String id = exchange.getResponse().getHeaders().getFirst(CorrelationIds.HEADER);
            subscription.dispose();
            var data = logs.fields(logs.find(id));
            assertEquals(0, data.get("httpStatus")); assertEquals("CANCELLED", data.get("completion"));
            assertEquals("CANCELLED", data.get("errorCategory")); assertEquals(1, logs.count(id));
        }
    }

    @Test void cancellationAfterCommitPreservesTheStatusAlreadySent() throws Exception {
        try (var logs = new RequestLogCapture()) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/venues"));
            var subscription = new CorrelationWebFilter().filter(exchange,
                    current -> current.getResponse().setComplete().then(Mono.never())).subscribe();
            String id = exchange.getResponse().getHeaders().getFirst(CorrelationIds.HEADER);
            subscription.dispose();
            var data = logs.fields(logs.find(id));
            assertEquals(200, data.get("httpStatus")); assertEquals("CANCELLED", data.get("completion"));
        }
    }

    @Test void observesStatusAssignedByAnOuterExceptionHandlerIncludingTimeout() throws Exception {
        try (var logs = new RequestLogCapture()) {
            for (int kind : new int[]{500, 503, 504}) {
                int status=kind==504?503:kind;
                var handler = WebHttpHandlerBuilder.webHandler(exchange -> Mono.error(kind == 504
                                ? new TimeoutException("sentinel-40") : new RuntimeException("password=sentinel-40")))
                        .filter(new CorrelationWebFilter())
                        .exceptionHandler((exchange, error) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.valueOf(status));
                            return exchange.getResponse().setComplete();
                        }).build();
                var response = new MockServerHttpResponse();
                handler.handle(MockServerHttpRequest.get("/api/v1/venues?password=sentinel-40").build(), response).block(Duration.ofSeconds(3));
                String id = response.getHeaders().getFirst(CorrelationIds.HEADER);
                var data = logs.fields(logs.find(id));
                assertEquals(status, data.get("httpStatus")); assertEquals(1, logs.count(id));
                assertEquals(kind == 504 ? "TIMEOUT" : kind==503 ? "UNAVAILABLE" : "INTERNAL", data.get("errorCategory"));
            }
        }
    }
}
