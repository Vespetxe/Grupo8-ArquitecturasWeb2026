package ar.edu.tudai.ticketya.servicio_entradas.reports;
import ar.edu.tudai.ticketya.servicio_entradas.repository.TicketRepository;
import ar.edu.tudai.ticketya.servicio_entradas.domain.TicketStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
@RestController
public class TicketReportController {
 private final TicketRepository tickets;
 public TicketReportController(TicketRepository tickets){this.tickets=tickets;}
 public record Sector(Long sectorId,long issuedTickets,long activeTickets,long annulledTickets,long admissions){}
 public record Snapshot(Long eventId,Instant generatedAt,List<Sector> sectors){}
 @GetMapping("/internal/reports/events/{eventId}/tickets") @Transactional(readOnly=true)
 public Snapshot report(@PathVariable Long eventId){if(eventId<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid event");Map<Long,long[]> result=new TreeMap<>();for(var ticket:tickets.findAllByEventId(eventId.toString())){long sector;try{sector=Long.parseLong(ticket.getSectorId());}catch(NumberFormatException invalid){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Ticket report source requires reconciliation");}if(sector<=0)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Invalid ticket sector");var counts=result.computeIfAbsent(sector,k->new long[4]);counts[0]++;if(ticket.getStatus()==TicketStatus.EMITIDA)counts[1]++;else counts[2]++;if(ticket.getValidatedAt()!=null)counts[3]++;}return new Snapshot(eventId,Instant.now(),result.entrySet().stream().map(e->new Sector(e.getKey(),e.getValue()[0],e.getValue()[1],e.getValue()[2],e.getValue()[3])).toList());}
}
