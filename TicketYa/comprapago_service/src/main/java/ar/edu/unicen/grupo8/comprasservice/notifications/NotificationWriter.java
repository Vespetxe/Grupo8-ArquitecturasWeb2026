package ar.edu.unicen.grupo8.comprasservice.notifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.context.ApplicationEventPublisher;
import ar.edu.unicen.grupo8.comprasservice.entity.*;
import ar.edu.unicen.grupo8.comprasservice.entity.enums.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;
@Service @Transactional(propagation=Propagation.MANDATORY)
public class NotificationWriter {
 public record Created(Long owner,Notification.View notification){}
 private final NotificationRepository repository;private final ApplicationEventPublisher events;private final Clock clock;
 public NotificationWriter(NotificationRepository repository,ApplicationEventPublisher events,Clock clock){this.repository=repository;this.events=events;this.clock=clock;}
 public void record(Long owner,String type,String kind,Long reference,String key,Instant occurred){
  if(owner==null)return; // Never infer an owner for historical records.
  if(owner<=0||reference==null||reference<=0)throw new IllegalArgumentException("Invalid notification identity");
  String id=UUID.nameUUIDFromBytes((key+":"+owner).getBytes(StandardCharsets.UTF_8)).toString();
  if(repository.existsById(id))return;
  repository.insert(id,owner,type,kind,reference,occurred);
  events.publishEvent(new Created(owner,new Notification.View(id,type,kind,reference,occurred,false,null)));
 }
 public void payment(Compra c,Pago p){
  if(p.getEstado()==EstadoPago.APROBADO && (c.getEtapa()==EtapaCompra.EMISION||c.getEstado()==EstadoCompra.COMPLETADA))
   record(c.getIdentityOwnerId(),"RESERVA_CONFIRMADA","RESERVA",c.getReservaId(),"reservation:"+c.getReservaId(),clock.instant());
  if(c.getEstado()==EstadoCompra.COMPLETADA && c.getEtapa()==EtapaCompra.FINALIZADA && !c.getEntradasIds().isEmpty()){
   record(c.getIdentityOwnerId(),"COMPRA_CONFIRMADA","COMPRA",c.getId(),"purchase:"+c.getId(),clock.instant());
   record(c.getIdentityOwnerId(),"ENTRADAS_EMITIDAS","COMPRA",c.getId(),"tickets:"+c.getId(),clock.instant());
  }
  if(c.getEstado()==EstadoCompra.FALLIDA && c.getEtapa()==EtapaCompra.FINALIZADA && p.getEstado()==EstadoPago.REINTEGRADO_TOTAL)
   record(c.getIdentityOwnerId(),"REINTEGRO_COMPLETADO","PAGO",p.getId(),"compensation:"+p.getId(),clock.instant());
 }
 public void cancelled(Compra c){if(c.getEstado()==EstadoCompra.CANCELADA&&c.getEtapa()==EtapaCompra.FINALIZADA)record(c.getIdentityOwnerId(),"COMPRA_CANCELADA","COMPRA",c.getId(),"cancelled:"+c.getId(),clock.instant());}
 public void refunded(Devolucion d){if(d.getEstado()==EstadoDevolucion.COMPLETADA&&d.getEtapa()==EtapaDevolucion.FINALIZADA)record(d.getCompra().getIdentityOwnerId(),"REINTEGRO_COMPLETADO","DEVOLUCION",d.getId(),"refund:"+d.getId(),clock.instant());}
}
