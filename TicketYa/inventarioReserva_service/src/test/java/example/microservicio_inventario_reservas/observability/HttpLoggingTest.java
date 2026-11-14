package example.microservicio_inventario_reservas.observability;

import example.microservicio_inventario_reservas.SecurityTestKeys;
import example.microservicio_inventario_reservas.dtos.DisponibilidadResponse;
import example.microservicio_inventario_reservas.exceptions.RecursoNoEncontradoException;
import example.microservicio_inventario_reservas.services.InventarioService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
class HttpLoggingTest {
    @Value("${local.server.port}") int port;
    @MockitoBean InventarioService inventory;
    private final HttpClient client = HttpClient.newHttpClient();
    private RequestLogCapture logs;
    @BeforeEach void setup() {
        logs = new RequestLogCapture();
        when(inventory.consultarDisponibilidad(anyLong(), anyLong())).thenAnswer(invocation -> {
            long event = invocation.getArgument(0);
            if (event == 400) throw new IllegalArgumentException("sentinel-40-invalid");
            if (event == 404) throw new RecursoNoEncontradoException("sentinel-40-missing");
            if (event == 409) throw new IllegalStateException("sentinel-40-conflict");
            if (event == 500) throw new RuntimeException("Authorization: sentinel-40", new RuntimeException("password=sentinel-40"));
            if (event == 503) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "sentinel-40-upstream");
            return new DisponibilidadResponse(event, 1L, false, 2, 2, List.of());
        });
    }
    @AfterEach void close() { logs.close(); }
    private HttpRequest request(String path, String token, String... ids) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        for (String id : ids) builder.header(CorrelationIds.HEADER, id);
        return builder.header("Cookie", "sentinel-40-cookie").header("X-Admin-Id", "999").GET().build();
    }
    private Map<String, Object> check(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode());
        String id = response.headers().firstValue(CorrelationIds.HEADER).orElseThrow();
        assertTrue(CorrelationIds.valid(id));
        var event = logs.find(id); var data = logs.fields(event);
        assertEquals(status, ((Number) data.get("httpStatus")).intValue());
        assertTrue(((Number) data.get("durationMs")).doubleValue() >= 0);
        assertEquals(1, logs.count(id)); assertNull(event.getThrowableProxy());
        var json = logs.json(event);
        assertTrue(json.at("/trace/id").asText().matches("[0-9a-f]{32}"));
        assertTrue(json.at("/span/id").asText().matches("[0-9a-f]{16}"));
        assertTrue(json.has("@timestamp")); assertTrue(json.at("/log/logger").isTextual());
        assertEquals("inventario-reservas", json.at("/service/name").asText());
        for (String secret : List.of("sentinel-40", "Authorization", "Cookie")) assertFalse(json.toString().contains(secret));
        return data;
    }
    @Test void servletReceivesW3cTraceButUsesItsOwnSpan() throws Exception {
        String trace="1234567890abcdef1234567890abcdef", parent="1234567890abcdef";
        var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/disponibilidad/eventos/1/sectores/1"))
                .header("traceparent","00-"+trace+"-"+parent+"-01").GET().build();
        var data=check(client.send(req,HttpResponse.BodyHandlers.ofString()),200);
        assertEquals(trace,data.get("trace.id"));
        assertNotEquals(parent,data.get("span.id"));
    }
    @Test void preservesOnlyCanonicalSingleUuidAndSubstitutesUntrustedMetadata() throws Exception {
        String valid = CorrelationIds.fresh();
        var first = client.send(request("/disponibilidad/eventos/1/sectores/1?password=sentinel-40", null, valid), HttpResponse.BodyHandlers.ofString());
        assertEquals(valid, first.headers().firstValue(CorrelationIds.HEADER).orElseThrow()); check(first, 200);
        for (String[] ids : List.of(new String[]{}, new String[]{"bad"}, new String[]{valid.toUpperCase(Locale.ROOT)}, new String[]{"x".repeat(1000)}, new String[]{valid, valid})) {
            var response = client.send(request("/disponibilidad/eventos/1/sectores/1", null, ids), HttpResponse.BodyHandlers.ofString());
            check(response, 200); assertNotEquals(valid, response.headers().firstValue(CorrelationIds.HEADER).orElseThrow());
        }
    }
    @Test void recordsActualHandledCodesAndVerifiedActorWithoutExternalMessages() throws Exception {
        String token = SecurityTestKeys.token("inventario", "user", "42", "CLIENTE", null, 300);
        for (int status : new int[]{200, 400, 404, 409, 500, 503}) {
            var response = client.send(request("/disponibilidad/eventos/" + status + "/sectores/1?secret=sentinel-40", token), HttpResponse.BodyHandlers.ofString());
            var data = check(response, status);
            assertEquals("USER", data.get("actorType")); assertEquals("42", data.get("actorId"));
            assertEquals("/disponibilidad/eventos/{id}/sectores/{id}", data.get("httpRoute"));
        }
    }
    @Test void capturesSecurityResponsesAndTechnicalActorOnlyAfterJwtVerification() throws Exception {
        for (String token : new String[]{null, "invalid", SecurityTestKeys.token("wrong", "user", "999", "ADMINISTRADOR", null, 300)}) {
            var data = check(client.send(request("/reservas?idCliente=999", token), HttpResponse.BodyHandlers.ofString()), 401);
            assertEquals("ANONYMOUS", data.get("actorType")); assertFalse(data.containsKey("actorId"));
            assertEquals("AUTHENTICATION", data.get("errorCategory"));
        }
        String staff = SecurityTestKeys.token("inventario", "user", "81", "PERSONAL_ACCESO", null, 300);
        var denied = check(client.send(request("/reservas?idCliente=81", staff), HttpResponse.BodyHandlers.ofString()), 403);
        assertEquals("81", denied.get("actorId")); assertEquals("AUTHORIZATION", denied.get("errorCategory"));
        String technical = SecurityTestKeys.token("inventario", "service", "service:eventos-to-inventario", null, "inventory.prepare", 120);
        var service = check(client.send(request("/internal/security/readiness", technical, CorrelationIds.fresh()), HttpResponse.BodyHandlers.ofString()), 200);
        assertEquals("SERVICE", service.get("actorType")); assertEquals("service:eventos-to-inventario", service.get("actorId"));
    }
    @Test void concurrentRequestsNeverShareActorsOrRequestState() throws Exception {
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        Set<String> traces=new HashSet<>();
        for (int i = 1; i <= 24; i++) {
            String id = CorrelationIds.fresh(); ids.add(id);
            String token = i % 2 == 0 ? SecurityTestKeys.token("inventario", "user", Integer.toString(i), "CLIENTE", null, 300) : null;
            futures.add(client.sendAsync(request("/disponibilidad/eventos/1/sectores/1?secret=sentinel-40", token, id), HttpResponse.BodyHandlers.ofString()));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);
        for (int i = 1; i <= futures.size(); i++) {
            var response = futures.get(i - 1).join(); var data = check(response, 200);
            assertTrue(traces.add((String)data.get("trace.id")));
            assertEquals(ids.get(i - 1), response.headers().firstValue(CorrelationIds.HEADER).orElseThrow());
            assertEquals(i % 2 == 0 ? "USER" : "ANONYMOUS", data.get("actorType"));
            if (i % 2 == 0) assertEquals(Integer.toString(i), data.get("actorId")); else assertFalse(data.containsKey("actorId"));
        }
    }
}
