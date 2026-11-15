package grupo22.eventocatalogo_service.notifications;
import grupo22.eventocatalogo_service.security.ServiceTokens;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.client.RestClient;
import com.mongodb.client.model.Filters;
import java.util.*;
@Component
@ConditionalOnProperty(name="ticketya.notifications.dispatch-enabled",havingValue="true",matchIfMissing=true)
public class EventNoticeDispatcher {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(EventNoticeDispatcher.class);
 private final EventNoticeStore store;private final MongoTemplate mongo;private final RestClient purchases;
 public EventNoticeDispatcher(EventNoticeStore store,MongoTemplate mongo,ServiceTokens tokens,@Value("${ticketya.compras.base-url:http://localhost:8084}")String url){this.store=store;this.mongo=mongo;this.purchases=tokens.client("compras",url);}
 public record Delivery(Long deliveryId,Long eventId,String type,java.time.Instant occurredAt,List<Long> recipients){}
 public void dispatch(){
  List<EventNoticeStore.Pending> pending;try{pending=store.pending();}catch(RuntimeException failure){LOG.warn("Notification outbox temporarily unavailable");return;}
  for(var notice:pending)try{
   var recipients=new TreeSet<Long>();
   if("ARTISTA_FAVORITO_PUBLICADO".equals(notice.type())&&notice.city()!=null&&!notice.participants().isEmpty())
    for(var document:mongo.getCollection("preferences").find(Filters.and(Filters.eq("preferredCity",notice.city()),Filters.in("favoriteParticipantIds",notice.participants())))){
     try{Long id=Long.valueOf(document.getString("_id"));if(id>0)recipients.add(id);}catch(NumberFormatException invalid){LOG.warn("Invalid preference recipient ignored");}
    }
   var ids=List.copyOf(recipients);
   if(!"ARTISTA_FAVORITO_PUBLICADO".equals(notice.type())||!ids.isEmpty())for(int start=0;start<Math.max(1,ids.size());start+=500){
    var body=new Delivery(notice.id(),notice.eventId(),notice.type(),notice.createdAt(),ids.subList(start,Math.min(start+500,ids.size())));
    var response=purchases.post().uri("/internal/notifications/events").body(body).retrieve().body(Ack.class);
    if(response==null||!Boolean.TRUE.equals(response.accepted()))throw new org.springframework.web.client.RestClientException("Unconfirmed notification delivery");
   }
   store.delivered(notice.id());
  }catch(RuntimeException failure){LOG.atWarn().addKeyValue("errorCategory","NOTIFICATION_DELIVERY_PENDING").log("Notification delivery remains pending");}
 }
 public record Ack(Boolean accepted){}
}
