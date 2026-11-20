package grupo22.eventocatalogo_service.notifications;
import grupo22.eventocatalogo_service.domain.*;
import grupo22.eventocatalogo_service.repository.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
@Service
public class EventNoticeStore {
 public record Pending(Long id,Long eventId,String type,Instant createdAt,String city,List<Long> participants){}
 private final EventNoticeRepository repository;private final VenueRepository venues;
 public EventNoticeStore(EventNoticeRepository repository,VenueRepository venues){this.repository=repository;this.venues=venues;}
 @Transactional(propagation=Propagation.MANDATORY)
 public void capture(Event event,String type){var notice=new EventNotice();notice.eventId=event.getEventId();notice.type=type;notice.createdAt=Instant.now();notice.city=venues.findById(event.getVenueId()).map(Venue::getCity).map(s->s.trim().toLowerCase(Locale.ROOT)).orElse(null);notice.participants=event.getParticipants().stream().map(Participant::getParticipantId).sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));repository.save(notice);}
 @Transactional(readOnly=true)
 public List<Pending> pending(){return repository.pending(org.springframework.data.domain.PageRequest.of(0,20)).stream().map(n->new Pending(n.id,n.eventId,n.type,n.createdAt,n.city,n.participants.isEmpty()?List.of():Arrays.stream(n.participants.split(",")).map(Long::valueOf).toList())).toList();}
 // Persist before dispatch so an unavailable destination cannot monopolize the next batch.
 @Transactional public void attempted(Long id){repository.attempted(id,Instant.now());}
 @Transactional public void delivered(Long id){repository.findById(id).orElseThrow().deliveredAt=Instant.now();}
}
