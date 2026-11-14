package ar.edu.unicen.grupo8.comprasservice.observability;

import ar.edu.unicen.grupo8.comprasservice.entity.enums.EtapaCompra;
import ar.edu.unicen.grupo8.comprasservice.exception.GlobalExceptionHandler;
import ar.edu.unicen.grupo8.comprasservice.security.TechnicalAuthFailure;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoggingSafetyTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SECRET = "sentinel-39-do-not-log";
    private static final String TAINTED = "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.secret.signature password=" + SECRET
            + " client_secret=" + SECRET + " Cookie=session-" + SECRET
            + " -----BEGIN PRIVATE KEY----- " + SECRET + " card=4111111111111111 cvv=987"
            + " claveIdempotencia=idem-" + SECRET + "\r\nforged-log-line";

    private JsonNode required(String line) {
        var json = JSON.readTree(line);
        Instant.parse(json.get("@timestamp").asText());
        assertTrue(json.at("/log/level").isTextual());
        assertTrue(json.at("/log/logger").isTextual());
        assertEquals("compras-service", json.at("/service/name").asText());
        assertTrue(json.get("message").isTextual());
        assertFalse(json.has("correlationId")); assertFalse(json.has("traceId"));
        assertFalse(json.has("spanId")); assertFalse(json.has("operationId"));
        return json;
    }

    private void noSecrets(String output) {
        for (String value : List.of(SECRET, "eyJhbGciOiJSUzI1NiJ9", "Authorization", "Cookie", "password=",
                "client_secret", "BEGIN PRIVATE KEY", "4111111111111111", "cvv=987", "claveIdempotencia", "forged-log-line"))
            assertFalse(output.contains(value), "Sensitive sentinel found in log output");
    }

    @Test @ResourceLock("SYSTEM_OUT")
    void nativeEcsConsoleIsValidJsonAndRejectsUnapprovedContextAndThrowableData() throws Exception {
        try (var fixture = new EcsLogFixture()) {
            var logger = fixture.context.getLogger("operational.console.test");
            fixture.context.getMDCAdapter().put("Authorization", TAINTED);
            fixture.context.getMDCAdapter().put("password", SECRET);
            var output = new ByteArrayOutputStream();
            PrintStream previous = System.out;
            try {
                System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
                var console = new ConsoleAppender<ILoggingEvent>();
                console.setContext(fixture.context); console.setEncoder(fixture.encoder);
                console.setTarget("System.out"); console.start(); logger.addAppender(console);
                var external = HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, TAINTED,
                        null, TAINTED.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
                SafeLog.refundPending(logger, 17L, external);
                logger.warn("Dependency failed", new RuntimeException(TAINTED, external));
                logger.atWarn().addKeyValue("requestBody", TAINTED).addKeyValue("clientSecret", SECRET)
                        .log("Unapproved structured fields are excluded");
                console.stop();
            } finally { System.setOut(previous); }
            String text = output.toString(StandardCharsets.UTF_8);
            var lines = text.lines().filter(s -> !s.isBlank()).toList();
            assertEquals(3, lines.size());
            var first = required(lines.get(0));
            assertEquals("REFUND_RECOVERY_PENDING", first.get("errorCode").asText());
            assertEquals("EXTERNAL_HTTP", first.get("errorCategory").asText());
            assertEquals(17L, first.get("devolucionId").asLong());
            for (String line : lines) { required(line); assertTrue(JSON.readTree(line).at("/error/stack_trace").isMissingNode()); }
            noSecrets(text);
        }
    }

    @Test void allRecoveryCodesKeepOnlyControlledDiagnostics() throws Exception {
        try (var fixture = new EcsLogFixture()) {
            var logger = fixture.context.getLogger("operational.recovery.test");
            var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
            RuntimeException failure = new RuntimeException(TAINTED, new DataIntegrityViolationException(TAINTED));
            SafeLog.refundPending(logger, 1L, failure);
            SafeLog.eventRefundPending(logger, 2L, 1L, failure);
            SafeLog.eventPurchasePending(logger, 2L, 3L, failure);
            SafeLog.purchaseReleasePending(logger, 3L, failure);
            SafeLog.paymentPending(logger, 3L, EtapaCompra.CONFIRMACION, new TechnicalAuthFailure());
            assertEquals(5, appender.list.size());
            for (var event : appender.list) {
                assertNull(event.getThrowableProxy());
                String text = fixture.encode(event); var json = required(text); noSecrets(text);
                assertTrue(json.has("errorCode")); assertTrue(json.has("exceptionType"));
                assertFalse(json.has("actor"));
            }
            assertEquals("PERSISTENCE", required(fixture.encode(appender.list.get(0))).get("rootErrorCategory").asText());
            assertEquals("TECHNICAL_AUTHENTICATION", required(fixture.encode(appender.list.get(4))).get("errorCategory").asText());
        }
    }

    @Test void genericHttp500KeepsContractWithoutLoggingExternalMessagesOrCauses() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var capture = new ListAppender<ILoggingEvent>(); capture.start(); logger.addAppender(capture);
        try (var fixture = new EcsLogFixture()) {
            var response = new GlobalExceptionHandler().handleErrorInterno(new RuntimeException(TAINTED,
                    new DataIntegrityViolationException(TAINTED)));
            assertEquals(500, response.getStatusCode().value());
            assertEquals(500, response.getBody().status());
            assertEquals("No se pudo completar la solicitud por un error interno", response.getBody().message());
            assertEquals(1, capture.list.size()); assertNull(capture.list.get(0).getThrowableProxy());
            String text = fixture.encode(capture.list.get(0)); noSecrets(text);
            var json = required(text);
            assertEquals("ERROR", json.at("/log/level").asText());
            assertEquals("REQUEST_UNEXPECTED_ERROR", json.get("errorCode").asText());
        } finally { logger.detachAppender(capture); capture.stop(); }
    }

    @Test void diagnosticLoggingNeverCallsUntrustedThrowableRendering() throws Exception {
        try (var fixture = new EcsLogFixture()) {
            var logger = fixture.context.getLogger("operational.hostile.test");
            var capture = new ListAppender<ILoggingEvent>(); capture.start(); logger.addAppender(capture);
            RuntimeException hostile = new RuntimeException() {
                @Override public String getMessage() { throw new AssertionError("getMessage must not run"); }
                @Override public String toString() { throw new AssertionError("toString must not run"); }
            };
            assertDoesNotThrow(() -> SafeLog.refundPending(logger, 1L, hostile));
            required(fixture.encode(capture.list.get(0)));
        }
    }

    @Test void databaseConflictKeeps409AndDiagnosticsWithoutSqlOrValues() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var capture = new ListAppender<ILoggingEvent>(); capture.start(); logger.addAppender(capture);
        try (var fixture = new EcsLogFixture()) {
            var response = new GlobalExceptionHandler().handlePersistencia(new DataIntegrityViolationException(
                    "SELECT * FROM usuarios WHERE password='" + SECRET + "'; " + TAINTED));
            assertEquals(409, response.getStatusCode().value());
            assertEquals(409, response.getBody().status());
            assertEquals(1, capture.list.size());
            String text = fixture.encode(capture.list.get(0)); noSecrets(text);
            assertFalse(text.contains("SELECT"));
            var json = required(text);
            assertEquals("PERSISTENCE_CONFLICT", json.get("errorCode").asText());
            assertEquals("PERSISTENCE", json.get("errorCategory").asText());
        } finally { logger.detachAppender(capture); capture.stop(); }
    }
}
