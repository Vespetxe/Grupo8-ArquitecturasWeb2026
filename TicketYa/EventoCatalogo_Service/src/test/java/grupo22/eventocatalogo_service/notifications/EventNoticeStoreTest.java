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
 @Test void twentyFailedOlderNoticesDoNotStarveLaterCommercialNotice(){
  // This Spring test uses only its in-memory H2 schema (src/test/resources).
  notices.deleteAll();
  var rows=new java.util.ArrayList<EventNotice>();
  for(int i=0;i<21;i++){var n=new EventNotice();n.eventId=id;n.type=i<20?"ARTISTA_FAVORITO_PUBLICADO":"EVENTO_CANCELADO";n.city="tandil";n.participants=i<20?"3":"";n.createdAt=java.time.Instant.parse("2020-01-01T00:00:00Z").plusSeconds(i);rows.add(n);}
  rows= new java.util.ArrayList<>(notices.saveAllAndFlush(rows));Long commercial=rows.get(20).id;
  var mongo=org.mockito.Mockito.mock(org.springframework.data.mongodb.core.MongoTemplate.class);
  org.mockito.Mockito.when(mongo.getCollection("preferences")).thenThrow(new com.mongodb.MongoException("Unavailable"));
  var remote=org.mockito.Mockito.mock(grupo22.eventocatalogo_service.feign.EventosComprasFeignClient.class);
  org.mockito.Mockito.when(remote.notify(org.mockito.ArgumentMatchers.any())).thenReturn(new EventNoticeDispatcher.Ack(true));
  new EventNoticeDispatcher(store,mongo,remote).dispatch();
  assertTrue(notices.findById(commercial).orElseThrow().deliveredAt==null);
  assertTrue(rows.subList(0,20).stream().allMatch(n->notices.findById(n.id).orElseThrow().lastAttemptAt!=null));
  // A new dispatcher uses persisted ordering, not a process-local cursor.
  new EventNoticeDispatcher(store,mongo,remote).dispatch();org.mockito.Mockito.verify(remote,org.mockito.Mockito.times(1)).notify(org.mockito.ArgumentMatchers.any());
  assertNotNull(notices.findById(commercial).orElseThrow().deliveredAt);
  var deliveredAt=notices.findById(commercial).orElseThrow().deliveredAt;
  store.attempted(commercial);assertEquals(deliveredAt,notices.findById(commercial).orElseThrow().deliveredAt);
  assertEquals(20,store.pending().size());
  assertTrue(store.pending().stream().allMatch(n->n.type().equals("ARTISTA_FAVORITO_PUBLICADO")));
  notices.deleteAllById(rows.stream().map(n->n.id).toList());
 }
 @Test void competingBusinessTransactionsCannotCommitDuplicateNotices()throws Exception{
  var barrier=new CyclicBarrier(2);var workers=Executors.newFixedThreadPool(2);try{Callable<Boolean> action=()->{try{tx.executeWithoutResult(s->{var event=events.findById(id).orElseThrow();try{barrier.await(5,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}event.setStatus(EventStatus.PUBLICADO);store.capture(event,"ARTISTA_FAVORITO_PUBLICADO");});return true;}catch(RuntimeException expected){return false;}};var a=workers.submit(action);var b=workers.submit(action);assertNotEquals(a.get(),b.get());}finally{workers.shutdownNow();}assertEquals(1,store.pending().stream().filter(n->n.eventId().equals(id)).count());
 }
}
