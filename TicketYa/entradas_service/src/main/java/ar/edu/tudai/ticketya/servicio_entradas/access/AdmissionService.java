package ar.edu.tudai.ticketya.servicio_entradas.access;
import ar.edu.tudai.ticketya.servicio_entradas.domain.TicketStatus;
import ar.edu.tudai.ticketya.servicio_entradas.repository.*;
import ar.edu.tudai.ticketya.servicio_entradas.security.CurrentActor;
import ar.edu.tudai.ticketya.servicio_entradas.observability.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;

@Service @Transactional
public class AdmissionService {
 private final EventAccessRepository assignments;
 private final EmissionEventGateRepository gates;
 private final TicketRepository tickets;
 public AdmissionService(EventAccessRepository assignments,EmissionEventGateRepository gates,TicketRepository tickets){this.assignments=assignments;this.gates=gates;this.tickets=tickets;}
 public record Assignment(String eventId,Long staffId,boolean assigned){}
 public record Result(boolean accepted,String code,String ticketId,String eventId,Instant validatedAt){}
 public record Decision(int status,Result result){}
 private void event(String event){if(event==null||!event.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))throw new IllegalArgumentException("Invalid event identifier");}
 public Assignment assign(String event,Long staff,boolean active){
  if(!CurrentActor.admin())throw new AccessDeniedException("Administrator required");
  event(event);if(staff==null||staff<=0)throw new IllegalArgumentException("Invalid staff identifier");
  // Same database mutex used by cancellation and admission; supports preassignment.
  gates.ensure(event);gates.lock(event).orElseThrow();
  var assignment=assignments.findByEventIdAndStaffId(event,staff).orElseGet(()->{var a=new EventAccess();a.eventId=event;a.staffId=staff;return a;});
  assignment.active=active;assignments.save(assignment);
  return new Assignment(event,staff,active);
 }
 private Decision reject(int status,String code,String event){return new Decision(status,new Result(false,code,null,event,null));}
 public Decision validate(String event,String qr){
  Long staff=CurrentActor.humanId();var authentication=SecurityContextHolder.getContext().getAuthentication();
  if(staff==null||authentication.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_PERSONAL_ACCESO")))throw new AccessDeniedException("Access personnel required");
  event(event);
  var gate=gates.lock(event).orElseThrow(()->new AccessDeniedException("Event assignment required"));
  if(!assignments.findByEventIdAndStaffId(event,staff).map(a->a.active).orElse(false))throw new AccessDeniedException("Event assignment required");
  if(qr==null||!qr.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))return reject(400,"INVALID_QR",event);
  if(gate.isCancelled())return reject(409,"EVENT_BLOCKED",event);
  var ticket=tickets.lockByQr(qr).orElse(null);
  if(ticket==null)return reject(404,"TICKET_NOT_FOUND",event);
  if(!event.equals(ticket.getEventId()))return reject(409,"EVENT_MISMATCH",event);
  if(ticket.getStatus()!=TicketStatus.EMITIDA||ticket.getBatch().isCancelled())return reject(409,"TICKET_NOT_ENABLED",event);
  if(ticket.getValidatedAt()!=null)return reject(409,"ALREADY_USED",event);
  ticket.recordAdmission(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS),staff);tickets.saveAndFlush(ticket);
  AuditTx.local(Audit.capture(Audit.Action.TICKET_VALIDATED,Audit.Resource.TICKET,ticket.getTicketId(),Audit.operation(Audit.Resource.TICKET,ticket.getTicketId()),Audit.Outcome.SUCCESS,null));
  return new Decision(200,new Result(true,"ACCESS_ACCEPTED",ticket.getTicketId(),event,ticket.getValidatedAt()));
 }
}
