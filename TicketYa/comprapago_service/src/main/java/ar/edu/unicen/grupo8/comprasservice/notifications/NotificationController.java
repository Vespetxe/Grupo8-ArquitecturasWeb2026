package ar.edu.unicen.grupo8.comprasservice.notifications;
import ar.edu.unicen.grupo8.comprasservice.security.CurrentActor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.List;
@RestController @RequestMapping("/api/v1/me/notifications")
public class NotificationController {
 private final NotificationRepository repository;private final NotificationPush push;private final Clock clock;
 public NotificationController(NotificationRepository repository,NotificationPush push,Clock clock){this.repository=repository;this.push=push;this.clock=clock;}
 private Long owner(){Long owner=CurrentActor.humanId();if(owner==null)throw new org.springframework.security.access.AccessDeniedException("Client required");return owner;}
 @GetMapping @Transactional(readOnly=true)
 public ResponseEntity<List<Notification.View>> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){if(page<0||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid pagination");return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(repository.findByOwnerIdOrderByCreatedAtDescIdDesc(owner(),PageRequest.of(page,size)).stream().map(Notification::view).toList());}
 public record Count(long unread){}
 @GetMapping("/unread-count") @Transactional(readOnly=true)
 public ResponseEntity<Count> count(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Count(repository.countByOwnerIdAndReadAtIsNull(owner())));}
 @PutMapping("/{id}/read") @Transactional
 public ResponseEntity<Notification.View> read(@PathVariable String id){Long owner=owner();repository.markRead(id,owner,clock.instant());return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(repository.findByIdAndOwnerId(id,owner).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification not found")).view());}
 @GetMapping(value="/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
 public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.SseEmitter> stream(){Long owner=owner();long remaining=java.time.Duration.between(clock.instant(),CurrentActor.jwt().getExpiresAt()).toMillis();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering","no").body(push.open(owner,Math.max(1,Math.min(30000,remaining))));}
}
