package example.microservicio_inventario_reservas.observability;
/** A timer run is a root trace, never a child of an unrelated HTTP request. */
@org.springframework.stereotype.Component
public class ReservationExpirationTask {
 private final example.microservicio_inventario_reservas.services.ReservaService reservations;
 private final io.micrometer.tracing.Tracer tracer;
 public ReservationExpirationTask(example.microservicio_inventario_reservas.services.ReservaService reservations,io.micrometer.tracing.Tracer tracer){this.reservations=reservations;this.tracer=tracer;}
 @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${reservas.expiration-interval-ms:30000}")
 public void run(){
  var span=tracer.spanBuilder().setNoParent().name("reservations.expiration").start();
  try(var scope=tracer.withSpan(span)){
   reservations.expirarReservasVencidas();
   org.slf4j.LoggerFactory.getLogger(getClass()).atInfo().addKeyValue("eventType","SCHEDULED_TASK").addKeyValue("trace.id",span.context().traceId()).addKeyValue("span.id",span.context().spanId()).log("Reservation expiration scan completed");
  }catch(RuntimeException failure){
   org.slf4j.LoggerFactory.getLogger(getClass()).atError().addKeyValue("eventType","SCHEDULED_TASK").addKeyValue("errorCategory","INTERNAL").addKeyValue("trace.id",span.context().traceId()).addKeyValue("span.id",span.context().spanId()).log("Reservation expiration scan failed");
   throw failure;
  }finally{span.end();}
 }
}
