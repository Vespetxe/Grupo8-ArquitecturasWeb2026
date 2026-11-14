package ar.edu.ticketya.gateway.observability;

import ar.edu.ticketya.gateway.SecurityTestKeys;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
class HttpLoggingTest {
    private static HttpServer upstream;
    private static ExecutorService executor;
    @Value("${local.server.port}") int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private RequestLogCapture logs;

    @DynamicPropertySource static void upstream(DynamicPropertyRegistry properties) throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(); upstream.setExecutor(executor);
        upstream.createContext("/", exchange -> {
            try {
                String query = Objects.toString(exchange.getRequestURI().getRawQuery(), "");
                if (query.contains("delay=true")) {
                    try { Thread.sleep(3500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                }
                var match = java.util.regex.Pattern.compile("status=([0-9]{3})").matcher(query);
                int status = match.find() ? Integer.parseInt(match.group(1)) : 200;
                String id = exchange.getRequestHeaders().getFirst(CorrelationIds.HEADER);
                String parent = exchange.getRequestHeaders().getFirst("traceparent");
                byte[] body = ("{\"seenCorrelation\":\"" + id + "\",\"seenTraceparent\":\""+parent+"\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set(CorrelationIds.HEADER, "untrusted-upstream");
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        upstream.start();
        for (String name : List.of("eventos", "inventario", "entradas", "compras"))
            properties.add("ticketya." + name + ".url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
        properties.add("spring.cloud.gateway.server.webflux.httpclient.response-timeout", () -> "2s");
    }
    @AfterAll static void stop() { upstream.stop(0); executor.shutdownNow(); }
    @BeforeEach void capture() { logs = new RequestLogCapture(); }
    @AfterEach void detach() { logs.close(); }

    private HttpRequest request(String path, String token, String supplied) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (supplied != null) builder.header(CorrelationIds.HEADER, supplied);
        return builder.header("X-Admin-Id", "999").header("Cookie", "sentinel-40-cookie").GET().build();
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
        assertEquals("api-gateway", json.at("/service/name").asText());
        String encoded = json.toString();
        for (String secret : List.of("sentinel-40", "Authorization", "Cookie", "untrusted-upstream")) assertFalse(encoded.contains(secret));
        return data;
    }

    @Test void w3cPropagationUsesOneTraceAndDifferentClientSpan() throws Exception {
        var response=client.send(request("/api/v1/venues",null,null),HttpResponse.BodyHandlers.ofString());
        var data=check(response,200);
        var body=new tools.jackson.databind.ObjectMapper().readTree(response.body());
        String parent=body.get("seenTraceparent").asText();
        assertTrue(parent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}"),parent);
        assertEquals(data.get("trace.id"),parent.split("-")[1]);
        assertNotEquals(data.get("span.id"),parent.split("-")[2]);
    }
    @Test void generatesFreshUuidAndPropagatesItWithoutTrustingClientOrUpstream() throws Exception {
        String external = CorrelationIds.fresh();
        for (String supplied : List.of(external, "not-a-uuid", external.toUpperCase(Locale.ROOT), "x".repeat(1000))) {
            var response = client.send(request("/api/v1/venues?password=sentinel-40", null, supplied), HttpResponse.BodyHandlers.ofString());
            var data = check(response, 200);
            String id = response.headers().firstValue(CorrelationIds.HEADER).orElseThrow();
            assertNotEquals(supplied, id); assertTrue(response.body().contains(id));
            assertEquals("ANONYMOUS", data.get("actorType")); assertFalse(data.containsKey("actorId"));
            assertEquals("/api/v1/venues", data.get("httpRoute"));
        }
    }

    @Test void preservesResponseCodesAndCapturesVerifiedActor() throws Exception {
        String token = SecurityTestKeys.token("gateway", "user", "42", "CLIENTE", null, 300);
        for (int status : new int[]{200, 400, 404, 409, 500, 503}) {
            var response = client.send(request("/reservas?status=" + status + "&password=sentinel-40", token, null), HttpResponse.BodyHandlers.ofString());
            var data = check(response, status);
            assertEquals("USER", data.get("actorType")); assertEquals("42", data.get("actorId"));
            assertEquals("/reservas", data.get("httpRoute"));
        }
    }

    @Test void security401And403AreObservedWithoutTrustingInvalidClaims() throws Exception {
        for (String token : new String[]{null, "invalid", SecurityTestKeys.token("wrong", "user", "999", "ADMINISTRADOR", null, 300)}) {
            var data = check(client.send(request("/reservas?idCliente=sentinel-40", token, null), HttpResponse.BodyHandlers.ofString()), 401);
            assertEquals("ANONYMOUS", data.get("actorType")); assertFalse(data.containsKey("actorId"));
            assertEquals("AUTHENTICATION", data.get("errorCategory"));
        }
        String staff = SecurityTestKeys.token("gateway", "user", "81", "PERSONAL_ACCESO", null, 300);
        var data = check(client.send(request("/reservas", staff, null), HttpResponse.BodyHandlers.ofString()), 403);
        assertEquals("81", data.get("actorId")); assertEquals("AUTHORIZATION", data.get("errorCategory"));
    }

    @Test void concurrentRequestsHaveIndependentActorsAndCorrelation() throws Exception {
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 1; i <= 24; i++) {
            String token = i % 2 == 0 ? SecurityTestKeys.token("gateway", "user", Integer.toString(i), "CLIENTE", null, 300) : null;
            futures.add(client.sendAsync(request("/api/v1/venues?secret=sentinel-40", token, CorrelationIds.fresh()), HttpResponse.BodyHandlers.ofString()));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);
        Set<String> ids = new HashSet<>();
        for (int i = 1; i <= futures.size(); i++) {
            var response = futures.get(i - 1).join(); var data = check(response, 200);
            assertTrue(ids.add(response.headers().firstValue(CorrelationIds.HEADER).orElseThrow()));
            assertEquals(i % 2 == 0 ? "USER" : "ANONYMOUS", data.get("actorType"));
            if (i % 2 == 0) assertEquals(Integer.toString(i), data.get("actorId")); else assertFalse(data.containsKey("actorId"));
        }
    }

    @Test void actualUpstreamTimeoutKeepsIts504AndIsCategorized() throws Exception {
        var data = check(client.send(request("/api/v1/venues?delay=true&secret=sentinel-40", null, null), HttpResponse.BodyHandlers.ofString()), 504);
        assertEquals("TIMEOUT", data.get("errorCategory"));
    }
}
