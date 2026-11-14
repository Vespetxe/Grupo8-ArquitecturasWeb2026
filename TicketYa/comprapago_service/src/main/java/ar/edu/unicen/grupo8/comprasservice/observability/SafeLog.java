package ar.edu.unicen.grupo8.comprasservice.observability;

import ar.edu.unicen.grupo8.comprasservice.entity.enums.EtapaCompra;
import ar.edu.unicen.grupo8.comprasservice.exception.ComunicacionPasarelaException;
import ar.edu.unicen.grupo8.comprasservice.security.TechnicalAuthFailure;
import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.dao.DataAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Operational diagnostics only. Never accepts a message, DTO, credentials or idempotency key. */
public final class SafeLog {
    private SafeLog() { }

    private enum Code {
        REFUND_RECOVERY_PENDING("Devolucion pendiente de recuperacion"),
        EVENT_REFUND_PENDING("Cancelacion con devolucion pendiente"),
        EVENT_PURCHASE_PENDING("Cancelacion con compra pendiente"),
        PURCHASE_RELEASE_PENDING("Compra cancelada con liberacion pendiente"),
        PAYMENT_RECOVERY_PENDING("Compra pendiente de recuperacion"),
        PERSISTENCE_CONFLICT("Conflicto de persistencia al procesar la solicitud"),
        REQUEST_UNEXPECTED_ERROR("Error interno al procesar la solicitud");

        private final String message;
        Code(String message) { this.message = message; }
    }

    private enum Category {
        TECHNICAL_AUTHENTICATION, EXTERNAL_HTTP, EXTERNAL_COMMUNICATION,
        PERSISTENCE, STATE_CONFLICT, INVALID_INPUT, UNEXPECTED
    }

    public static void refundPending(Logger log, Long id, RuntimeException failure) {
        record(log, Code.REFUND_RECOVERY_PENDING, failure, null, id, null, null);
    }

    public static void eventRefundPending(Logger log, Long eventId, Long refundId, RuntimeException failure) {
        record(log, Code.EVENT_REFUND_PENDING, failure, null, refundId, eventId, null);
    }

    public static void eventPurchasePending(Logger log, Long eventId, Long purchaseId, RuntimeException failure) {
        record(log, Code.EVENT_PURCHASE_PENDING, failure, purchaseId, null, eventId, null);
    }

    public static void purchaseReleasePending(Logger log, Long purchaseId, RuntimeException failure) {
        record(log, Code.PURCHASE_RELEASE_PENDING, failure, purchaseId, null, null, null);
    }

    public static void paymentPending(Logger log, Long purchaseId, EtapaCompra phase, RuntimeException failure) {
        record(log, Code.PAYMENT_RECOVERY_PENDING, failure, purchaseId, null, null, phase);
    }

    public static void unexpectedRequest(Logger log, Exception failure) {
        record(log, Code.REQUEST_UNEXPECTED_ERROR, failure, null, null, null, null);
    }

    public static void persistenceConflict(Logger log, Exception failure) {
        record(log, Code.PERSISTENCE_CONFLICT, failure, null, null, null, null);
    }

    private static void record(Logger log, Code code, Throwable failure, Long purchaseId,
                               Long refundId, Long eventId, EtapaCompra phase) {
        LoggingEventBuilder event = code == Code.REQUEST_UNEXPECTED_ERROR ? log.atError() : log.atWarn();
        event.addKeyValue("errorCode", code.name())
                .addKeyValue("errorCategory", category(failure).name())
                .addKeyValue("exceptionType", safeType(failure))
                .addKeyValue("rootErrorCategory", category(rootCause(failure)).name());
        if (purchaseId != null) event.addKeyValue("compraId", purchaseId);
        if (refundId != null) event.addKeyValue("devolucionId", refundId);
        if (eventId != null) event.addKeyValue("eventoId", eventId);
        if (phase != null) event.addKeyValue("etapa", phase.name());
        // Code location is useful without Throwable messages, causes or HTTP response bodies.
        for (StackTraceElement frame : failure.getStackTrace()) {
            if (frame.getClassName().startsWith("ar.edu.unicen.grupo8.comprasservice.")
                    && !frame.getClassName().startsWith(SafeLog.class.getPackageName() + ".")) {
                event.addKeyValue("errorOrigin", frame.getClassName() + "#" + frame.getMethodName()
                        + ":" + frame.getLineNumber());
                break;
            }
        }
        // Deliberately no setCause(), getMessage(), toString(), arguments or arbitrary context map.
        event.log(code.message);
    }

    private static Category category(Throwable failure) {
        if (failure instanceof TechnicalAuthFailure) return Category.TECHNICAL_AUTHENTICATION;
        if (failure instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            return status == 401 || status == 403 ? Category.TECHNICAL_AUTHENTICATION : Category.EXTERNAL_HTTP;
        }
        if (failure instanceof RestClientException || failure instanceof ComunicacionPasarelaException
                || failure instanceof java.io.IOException) return Category.EXTERNAL_COMMUNICATION;
        if (failure instanceof DataAccessException) return Category.PERSISTENCE;
        if (failure instanceof IllegalStateException) return Category.STATE_CONFLICT;
        if (failure instanceof IllegalArgumentException) return Category.INVALID_INPUT;
        return Category.UNEXPECTED;
    }

    private static String safeType(Throwable failure) {
        if (failure instanceof TechnicalAuthFailure) return "TechnicalAuthFailure";
        if (failure instanceof RestClientResponseException) return "RestClientResponseException";
        if (failure instanceof RestClientException) return "RestClientException";
        if (failure instanceof ComunicacionPasarelaException) return "ComunicacionPasarelaException";
        if (failure instanceof DataAccessException) return "DataAccessException";
        if (failure instanceof java.io.IOException) return "IOException";
        if (failure instanceof IllegalStateException) return "IllegalStateException";
        if (failure instanceof IllegalArgumentException) return "IllegalArgumentException";
        return failure instanceof RuntimeException ? "RuntimeException" : "Exception";
    }

    private static Throwable rootCause(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable root = failure;
        for (int i = 0; i < 8 && seen.add(root); i++) {
            Throwable next = root.getCause();
            if (next == null || seen.contains(next)) break;
            root = next;
        }
        return root;
    }
}
