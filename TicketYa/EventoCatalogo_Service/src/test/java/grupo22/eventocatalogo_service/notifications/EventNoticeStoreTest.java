package grupo22.eventocatalogo_service.notifications;
import grupo22.eventocatalogo_service.domain.*;
import grupo22.eventocatalogo_service.repository.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.*;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class EventNoticeStoreTest {
 @Autowired EventNoticeStore store;@Autowired EventNoticeRepository notices;@Autowired EventRepository events;@Autowired VenueRepository venues;@Autowired PlatformTransactionManager manager;
 TransactionTemplate tx;Long id;
 @BeforeEach void setup(){tx=new TransactionTemplate(manager);id=tx.execute(s->{var venue=new Venue("Outbox", "Address",BigDecimal.ONE,BigDecimal.ONE);venue.setCity("Tandil");venue=venues.save(venue);var event=new Event("Outbox "+java.util.UUID.randomUUID(),null,"Recital",1L,venue.getVenueId(),OffsetDateTime.now().plusDays(8));return events.saveAndFlush(event).getEventId();});}
 @Test void rolledBackBusinessChangeLeavesNoNotice(){tx.executeWithoutResult(s->{var event=events.findById(id).orElseThrow();event.setStatus(EventStatus.PUBLICADO);store.capture(event,"ARTISTA_FAVORITO_PUBLICADO");s.setRollbackOnly();});assertEquals(EventStatus.BORRADOR,events.findById(id).orElseThrow().getStatus());assertTrue(store.pending().stream().noneMatch(n->n.eventId().equals(id)));}
 @Test void committedNoticePersistsUntilAcknowledged(){tx.executeWithoutResult(s->{var event=events.findById(id).orElseThrow();event.setStatus(EventStatus.PUBLICADO);store.capture(event,"ARTISTA_FAVORITO_PUBLICADO");});var pending=store.pending().stream().filter(n->n.eventId().equals(id)).findFirst().orElseThrow();assertEquals("tandil",pending.city());store.delivered(pending.id());assertTrue(store.pending().stream().noneMatch(n->n.eventId().equals(id)));}
 @Test void competingBusinessTransactionsCannotCommitDuplicateNotices()throws Exception{
  var barrier=new CyclicBarrier(2);var workers=Executors.newFixedThreadPool(2);try{Callable<Boolean> action=()->{try{tx.executeWithoutResult(s->{var event=events.findById(id).orElseThrow();try{barrier.await(5,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}event.setStatus(EventStatus.PUBLICADO);store.capture(event,"ARTISTA_FAVORITO_PUBLICADO");});return true;}catch(RuntimeException expected){return false;}};var a=workers.submit(action);var b=workers.submit(action);assertNotEquals(a.get(),b.get());}finally{workers.shutdownNow();}assertEquals(1,store.pending().stream().filter(n->n.eventId().equals(id)).count());
 }
}
