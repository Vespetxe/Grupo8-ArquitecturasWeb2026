package example.microservicio_inventario_reservas.reports;
import example.microservicio_inventario_reservas.repositories.InventarioSectorRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
@RestController
public class InventoryReportController {
 private final InventarioSectorRepository inventory;
 public InventoryReportController(InventarioSectorRepository inventory){this.inventory=inventory;}
 public record Sector(Long sectorId,long capacity,long available,String state){}
 public record Snapshot(Long eventId,Instant generatedAt,List<Sector> sectors){}
 @GetMapping("/internal/reports/events/{eventId}/inventory") @Transactional(readOnly=true)
 public Snapshot report(@PathVariable Long eventId){if(eventId<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid event");return new Snapshot(eventId,Instant.now(),inventory.findAllByIdEventoOrderByIdSector(eventId).stream().map(i->new Sector(i.getIdSector(),i.getCapacidadTotal(),i.getDisponibles(),i.getEstado().name())).toList());}
}
