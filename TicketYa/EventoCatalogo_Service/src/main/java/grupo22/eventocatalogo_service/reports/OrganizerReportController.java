package grupo22.eventocatalogo_service.reports;
import grupo22.eventocatalogo_service.repository.*;
import grupo22.eventocatalogo_service.security.*;
import grupo22.eventocatalogo_service.resilience.PilotReadGuard;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import java.time.Instant;
import java.math.*;
import java.util.*;
@RestController
public class OrganizerReportController {
 private final EventRepository events;private final SectorRepository sectors;private final TransactionTemplate read;
 private final RestClient sales,tickets,inventory;private final PilotReadGuard salesGuard,ticketsGuard,inventoryGuard;
 public OrganizerReportController(EventRepository events,SectorRepository sectors,PlatformTransactionManager manager,ServiceTokens tokens,Environment env,@Value("${ticketya.compras.base-url:http://localhost:8084}")String salesUrl,@Value("${ticketya.entradas.base-url:http://localhost:8083}")String ticketsUrl,@Value("${ticketya.inventario.base-url:http://localhost:8082}")String inventoryUrl){this.events=events;this.sectors=sectors;read=new TransactionTemplate(manager);read.setReadOnly(true);sales=tokens.client("compras",salesUrl);tickets=tokens.client("entradas",ticketsUrl);inventory=tokens.client("inventario",inventoryUrl);salesGuard=new PilotReadGuard(env,PilotReadGuard.Family.REPORT_SALES);ticketsGuard=new PilotReadGuard(env,PilotReadGuard.Family.REPORT_TICKETS);inventoryGuard=new PilotReadGuard(env,PilotReadGuard.Family.REPORT_INVENTORY);}
 public record SalesSector(Long sectorId,Long soldTickets,Long returnedTickets,BigDecimal grossCollected,BigDecimal refundedAmount,BigDecimal netCollected){}
 public record Sales(Long eventId,Instant generatedAt,boolean financialComplete,List<SalesSector> sectors){}
 public record TicketSector(Long sectorId,Long issuedTickets,Long activeTickets,Long annulledTickets,Long admissions){}
 public record Tickets(Long eventId,Instant generatedAt,List<TicketSector> sectors){}
 public record InventorySector(Long sectorId,Long capacity,Long available,String state){}
 public record Inventory(Long eventId,Instant generatedAt,List<InventorySector> sectors){}
 public record CatalogueSector(Long id,String name,String type){}
 private record Catalogue(Long eventId,String name,String status,List<CatalogueSector> sectors){}
 public record Metrics(long soldTickets,long returnedTickets,long netSoldTickets,BigDecimal grossCollected,BigDecimal refundedAmount,BigDecimal netCollected,Long capacity,Long available,BigDecimal occupancyPercent,long issuedTickets,long activeTickets,long annulledTickets,long admissions){}
 public record Sector(Long sectorId,String name,String type,String inventoryState,boolean ticketsMatchSales,Metrics metrics){}
 public record Report(Long eventId,String eventName,String eventStatus,Instant generatedAt,String amountUnit,String period,boolean financialComplete,boolean sourcesAligned,Map<String,Instant> sourceGeneratedAt,Metrics totals,List<Sector> sectors){}
 private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Report source unavailable or awaiting reconciliation");}
 private static void require(boolean ok){if(!ok)throw unavailable();}
 private static BigDecimal percent(long sold,Long capacity){return capacity==null||capacity==0?null:BigDecimal.valueOf(sold).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(capacity),2,RoundingMode.HALF_UP);}
 @GetMapping("/api/v1/events/{eventId}/report")
 public ResponseEntity<Report> report(@PathVariable Long eventId){
  if(eventId<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid event");
  Catalogue catalogue=read.execute(status->{var event=events.findById(eventId).orElseThrow(CurrentActor::missing);if(!Objects.equals(CurrentActor.humanId(),event.getIdentityOwnerId())||event.getIdentityOwnerId()==null)throw CurrentActor.missing();return new Catalogue(eventId,event.getName(),event.getStatus().name(),sectors.findByVenueVenueId(event.getVenueId()).stream().sorted(Comparator.comparing(grupo22.eventocatalogo_service.domain.Sector::getSectorId)).map(s->new CatalogueSector(s.getSectorId(),s.getName(),s.getType().name())).toList());});
  Sales s;Tickets t;Inventory i;
  try{s=salesGuard.execute(()->sales.get().uri("/internal/reports/events/{id}/sales",eventId).retrieve().body(Sales.class));t=ticketsGuard.execute(()->tickets.get().uri("/internal/reports/events/{id}/tickets",eventId).retrieve().body(Tickets.class));i=inventoryGuard.execute(()->inventory.get().uri("/internal/reports/events/{id}/inventory",eventId).retrieve().body(Inventory.class));}catch(RestClientException failure){throw unavailable();}
  require(s!=null&&t!=null&&i!=null&&eventId.equals(s.eventId())&&eventId.equals(t.eventId())&&eventId.equals(i.eventId())&&s.generatedAt()!=null&&t.generatedAt()!=null&&i.generatedAt()!=null&&s.sectors()!=null&&t.sectors()!=null&&i.sectors()!=null);
  Set<Long> known=new HashSet<>();catalogue.sectors().forEach(c->known.add(c.id()));Map<Long,SalesSector> saleRows=new HashMap<>();for(var row:s.sectors()){require(row!=null&&known.contains(row.sectorId())&&!saleRows.containsKey(row.sectorId())&&row.soldTickets()!=null&&row.returnedTickets()!=null&&row.soldTickets()>=0&&row.returnedTickets()>=0&&row.returnedTickets()<=row.soldTickets()&&row.grossCollected()!=null&&row.refundedAmount()!=null&&row.grossCollected().signum()>=0&&row.refundedAmount().signum()>=0&&row.refundedAmount().compareTo(row.grossCollected())<=0);require(s.financialComplete()?row.netCollected()!=null&&row.netCollected().compareTo(row.grossCollected().subtract(row.refundedAmount()))==0:row.netCollected()==null);saleRows.put(row.sectorId(),row);}
  Map<Long,TicketSector> ticketRows=new HashMap<>();for(var row:t.sectors()){require(row!=null&&known.contains(row.sectorId())&&!ticketRows.containsKey(row.sectorId())&&row.issuedTickets()!=null&&row.activeTickets()!=null&&row.annulledTickets()!=null&&row.admissions()!=null&&row.issuedTickets()>=0&&row.activeTickets()>=0&&row.annulledTickets()>=0&&row.admissions()>=0&&row.activeTickets()+row.annulledTickets()==row.issuedTickets()&&row.admissions()<=row.issuedTickets());ticketRows.put(row.sectorId(),row);}
  Map<Long,InventorySector> stockRows=new HashMap<>();for(var row:i.sectors()){require(row!=null&&known.contains(row.sectorId())&&!stockRows.containsKey(row.sectorId())&&row.capacity()!=null&&row.available()!=null&&row.capacity()>=0&&row.available()>=0&&row.available()<=row.capacity()&&row.state()!=null);stockRows.put(row.sectorId(),row);}
  List<Sector> result=new ArrayList<>();for(var c:catalogue.sectors()){
   var a=saleRows.getOrDefault(c.id(),new SalesSector(c.id(),0L,0L,BigDecimal.ZERO,BigDecimal.ZERO,s.financialComplete()?BigDecimal.ZERO:null));var b=ticketRows.getOrDefault(c.id(),new TicketSector(c.id(),0L,0L,0L,0L));var stock=stockRows.get(c.id());Long capacity=stock==null?null:stock.capacity(),available=stock==null?null:stock.available();long net=a.soldTickets()-a.returnedTickets();
   result.add(new Sector(c.id(),c.name(),c.type(),stock==null?"NO_INICIALIZADO":stock.state(),net==b.activeTickets()&&(capacity==null||net<=capacity),new Metrics(a.soldTickets(),a.returnedTickets(),net,a.grossCollected(),a.refundedAmount(),a.netCollected(),capacity,available,percent(net,capacity),b.issuedTickets(),b.activeTickets(),b.annulledTickets(),b.admissions())));
  }
  var all=result.stream().map(Sector::metrics).toList();long sold=all.stream().mapToLong(Metrics::soldTickets).sum(),returned=all.stream().mapToLong(Metrics::returnedTickets).sum();BigDecimal gross=all.stream().map(Metrics::grossCollected).reduce(BigDecimal.ZERO,BigDecimal::add),refunded=all.stream().map(Metrics::refundedAmount).reduce(BigDecimal.ZERO,BigDecimal::add);Long capacity=all.isEmpty()||all.stream().anyMatch(m->m.capacity()==null)?null:all.stream().mapToLong(Metrics::capacity).sum(),available=all.isEmpty()||all.stream().anyMatch(m->m.available()==null)?null:all.stream().mapToLong(Metrics::available).sum();
  var totals=new Metrics(sold,returned,sold-returned,gross,refunded,s.financialComplete()?gross.subtract(refunded):null,capacity,available,percent(sold-returned,capacity),all.stream().mapToLong(Metrics::issuedTickets).sum(),all.stream().mapToLong(Metrics::activeTickets).sum(),all.stream().mapToLong(Metrics::annulledTickets).sum(),all.stream().mapToLong(Metrics::admissions).sum());
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Report(eventId,catalogue.name(),catalogue.status(),Instant.now(),"UNIDAD_MONETARIA_DEL_SISTEMA","ACUMULADO_DEL_EVENTO",s.financialComplete(),result.stream().allMatch(Sector::ticketsMatchSales),Map.of("compras",s.generatedAt(),"entradas",t.generatedAt(),"inventario",i.generatedAt()),totals,List.copyOf(result)));
 }
}
