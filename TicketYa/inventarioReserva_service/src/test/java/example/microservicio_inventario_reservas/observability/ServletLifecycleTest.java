package example.microservicio_inventario_reservas.observability;

import jakarta.servlet.AsyncEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class ServletLifecycleTest {
    @Test void asyncTimeoutLogsOnlyAtCompletionAndPreservesStatus() throws Exception {
        try (var logs = new RequestLogCapture()) {
            var before = MDC.getCopyOfContextMap();
            var request = new MockHttpServletRequest("GET", "/disponibilidad/eventos/1/sectores/1");
            request.setAsyncSupported(true); request.setQueryString("password=sentinel-40");
            var response = new MockHttpServletResponse();
            new HttpLoggingFilter().doFilter(request, response, (req, res) -> req.startAsync(req, res));
            String id = response.getHeader(CorrelationIds.HEADER);
            assertEquals(0, logs.count(id));
            var async = (MockAsyncContext) request.getAsyncContext();
            for (var listener : async.getListeners()) listener.onTimeout(new AsyncEvent(async));
            response.setStatus(503); async.complete();
            var data = logs.fields(logs.find(id));
            assertEquals(503, data.get("httpStatus")); assertEquals("TIMEOUT", data.get("errorCategory"));
            assertEquals(1, logs.count(id)); assertEquals(before, MDC.getCopyOfContextMap());
        }
    }

    @Test void cancellationDoesNotForgeAnHttpResponseOrLeakThreadContext() throws Exception {
        try (var logs = new RequestLogCapture()) {
            var before = MDC.getCopyOfContextMap();
            var request = new MockHttpServletRequest("GET", "/disponibilidad/eventos/1/sectores/1");
            var response = new MockHttpServletResponse();
            assertThrows(CancellationException.class, () -> new HttpLoggingFilter().doFilter(request, response,
                    (req, res) -> { throw new CancellationException("Authorization: sentinel-40"); }));
            String id = response.getHeader(CorrelationIds.HEADER);
            var data = logs.fields(logs.find(id));
            assertEquals(0, data.get("httpStatus")); assertEquals("CANCELLED", data.get("completion"));
            assertEquals(before, MDC.getCopyOfContextMap());
        }
    }

    @Test void strictUuidAndRouteNormalizationNeverIncludeUntrustedText() {
        for (String value : List.of("", "bad", "x".repeat(1000), CorrelationIds.fresh().toUpperCase(java.util.Locale.ROOT), "00000000-0000-0000-0000-000000000000"))
            assertFalse(CorrelationIds.valid(value));
        assertTrue(CorrelationIds.valid(CorrelationIds.internal(List.of("bad"))));
        assertEquals("/unmapped", HttpRoutes.route("/private/password-sentinel-40"));
        assertEquals("/reservas/{id}", HttpRoutes.route("/reservas/123"));
        assertEquals("OTHER", HttpRoutes.method("sentinel-40"));
    }

    @Test void asyncFailureAfterHeadersKeepsSentStatusButDoesNotClaimSuccess() throws Exception {
        try (var logs = new RequestLogCapture()) {
            var request = new MockHttpServletRequest("GET", "/disponibilidad/eventos/1/sectores/1");
            request.setAsyncSupported(true);
            var response = new MockHttpServletResponse();
            new HttpLoggingFilter().doFilter(request, response, (req, res) -> req.startAsync(req, res));
            String id = response.getHeader(CorrelationIds.HEADER);
            response.flushBuffer();
            var async = (MockAsyncContext) request.getAsyncContext();
            for (var listener : async.getListeners()) listener.onError(new AsyncEvent(async, new RuntimeException("sentinel-40")));
            async.complete();
            var data = logs.fields(logs.find(id));
            assertEquals(200, data.get("httpStatus"));
            assertEquals("ERROR", data.get("completion")); assertEquals("INTERNAL", data.get("errorCategory"));
        }
    }
}
