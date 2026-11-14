package example.microservicio_inventario_reservas.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="reservas.expiration-interval-ms=3600000")
@ContextConfiguration(initializers=example.microservicio_inventario_reservas.SecurityTestKeys.class)
class ScheduledTracingTest {
    @Autowired ReservationExpirationTask task;
    @Autowired io.micrometer.tracing.Tracer tracer;
    @MockitoBean example.microservicio_inventario_reservas.services.ReservaService reservations;

    @Test void timerStartsIndependentRootsAndRestoresCallerContext() {
        var observed=new java.util.ArrayList<String>();
        doAnswer(call->{observed.add(tracer.currentSpan().context().traceId());return null;})
                .when(reservations).expirarReservasVencidas();
        var parent=tracer.nextSpan().name("unrelated-http").start();
        try(var scope=tracer.withSpan(parent)) {
            task.run();task.run();
            assertEquals(parent.context().traceId(),tracer.currentSpan().context().traceId());
        } finally {parent.end();}
        assertEquals(2,observed.stream().distinct().count());
        assertFalse(observed.contains(parent.context().traceId()));
        assertNull(tracer.currentSpan());
    }

    @Test void failureAlsoClosesTimerScope() {
        doThrow(new IllegalStateException("sentinel-41-secret")).when(reservations).expirarReservasVencidas();
        assertThrows(IllegalStateException.class,task::run);
        assertNull(tracer.currentSpan());
    }
}
