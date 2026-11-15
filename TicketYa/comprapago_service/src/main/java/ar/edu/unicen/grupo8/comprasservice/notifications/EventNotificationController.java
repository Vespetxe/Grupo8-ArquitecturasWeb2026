package ar.edu.unicen.grupo8.comprasservice.notifications;
import ar.edu.unicen.grupo8.comprasservice.repository.CompraRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;
@RestController
public class EventNotificationController {
 public record Notice(Long deliveryId,Long eventId,String type,Instant occurredAt,List<Long> recipients){}
 private final NotificationWriter writer;private final CompraRepository purchases;
 public EventNotificationController(NotificationWriter writer,CompraRepository purchases){this.writer=writer;this.purchases=purchases;}
 @PostMapping("/internal/notifications/events") @Transactional
 public Map<String,Boolean> accept(@RequestBody Notice notice){
  if(notice.deliveryId()==null||notice.deliveryId()<=0||notice.eventId()==null||notice.eventId()<=0||notice.occurredAt()==null||!Set.of("ARTISTA_FAVORITO_PUBLICADO","EVENTO_CANCELADO","EVENTO_CAMBIADO").contains(Objects.toString(notice.type(),""))||notice.recipients()==null||notice.recipients().size()>500||notice.recipients().stream().anyMatch(id->id==null||id<=0))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid event notification");
  Set<Long> owners=new TreeSet<>();if("ARTISTA_FAVORITO_PUBLICADO".equals(notice.type()))owners.addAll(notice.recipients());else purchases.findByEventoId(notice.eventId()).stream().filter(c->c.getIdentityOwnerId()!=null&&!c.getEntradasIds().isEmpty()).forEach(c->owners.add(c.getIdentityOwnerId()));
  for(Long owner:owners)writer.record(owner,notice.type(),"EVENTO",notice.eventId(),"event:"+notice.deliveryId(),notice.occurredAt());return Map.of("accepted",true);
 }
}
