package grupo22.eventocatalogo_service.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import grupo22.eventocatalogo_service.domain.Participant;
import grupo22.eventocatalogo_service.repository.ParticipantRepository;
import grupo22.eventocatalogo_service.service.ParticipantService;
import grupo22.eventocatalogo_service.DTO.request.ParticipantRequestDTO;
import grupo22.eventocatalogo_service.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"ticketya.catalogue-cache.max-participants=2","ticketya.catalogue-cache.list-max-items=2"})
class CatalogueCacheTest {
 @Autowired ParticipantService service;
 @MockitoSpyBean ParticipantRepository participants;
 @Autowired PublicCatalogueCache cache;
 @Autowired @Qualifier("catalogueCacheManager") CacheManager manager;
 @Autowired PlatformTransactionManager transactions;
 @Autowired DataSource dataSource;
 @MockitoBean(name="catalogueTicker") Ticker ticker;
 final AtomicLong clock=new AtomicLong();
 Long id;
 @BeforeEach void prepare()throws Exception{
  try(var c=dataSource.getConnection()){assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:mem:"));}
  when(ticker.read()).thenAnswer(i->clock.get());
  cache.invalidateAfterCommit();
  id=participants.save(new Participant("Cache fixture", "Banda")).getParticipantId();
  clearInvocations(participants);
 }
 @AfterEach void clear(){cache.invalidateAfterCommit();}
 CaffeineCache nativeCache(String name){return (CaffeineCache)manager.getCache(name);}
 ParticipantRequestDTO request(String name){return new ParticipantRequestDTO(name,"Banda");}
 @Test void missesThenHitsWithoutSharingMutableResponse(){
  var first=service.getParticipantById(id);first.setName("poisoned response");
  assertEquals("Cache fixture",service.getParticipantById(id).getName());
  verify(participants,times(1)).findById(id);
  assertTrue(nativeCache(CatalogueCacheConfiguration.DETAILS).getNativeCache().stats().hitCount()>0);
 }
 @Test void listIsCached(){
  // A dedicated manager with a generous list limit isolates this assertion from other H2 fixtures.
  var cfg=new CatalogueCacheConfiguration();var local=(SimpleCacheManager)cfg.catalogueCacheManager(ticker,30,20,1000);local.afterPropertiesSet();
  var c=new PublicCatalogueCache(participants,local,transactions);
  assertEquals(c.all(),c.all());verify(participants,times(1)).findAll();
 }
 @Test void expiryIsAfterWriteAndDoesNotExtendOnRead(){
  service.getParticipantById(id);
  new TransactionTemplate(transactions).executeWithoutResult(s->{var p=participants.findById(id).orElseThrow();p.setName("external committed change");participants.save(p);});
  clearInvocations(participants);
  clock.set(TimeUnit.SECONDS.toNanos(29));assertEquals("Cache fixture",service.getParticipantById(id).getName());
  clock.set(TimeUnit.SECONDS.toNanos(31));assertEquals("external committed change",service.getParticipantById(id).getName());
  verify(participants,times(1)).findById(id);
 }
 @Test void committedCrudInvalidatesListAndDetail(){
  service.getParticipantById(id);service.getAllParticipants();
  service.updateParticipant(id,request("updated"));assertEquals("updated",service.getParticipantById(id).getName());
  var created=service.createParticipant(request("created"));assertTrue(service.getAllParticipants().stream().anyMatch(p->p.getParticipantId().equals(created.getParticipantId())));
  service.deleteParticipant(id);assertThrows(ResourceNotFoundException.class,()->service.getParticipantById(id));
 }
 @Test void rollbackDoesNotPublishUncommittedDataOrInvalidateCommittedSnapshot(){
  service.getParticipantById(id);clearInvocations(participants);
  new TransactionTemplate(transactions).executeWithoutResult(s->{service.updateParticipant(id,request("uncommitted"));assertEquals("uncommitted",service.getParticipantById(id).getName());s.setRollbackOnly();});
  clearInvocations(participants);
  assertEquals("Cache fixture",service.getParticipantById(id).getName());verify(participants,never()).findById(id);
  assertEquals("Cache fixture",participants.findById(id).orElseThrow().getName());
 }
 @Test void concurrentRequestsCoalesceSingleKeyLoad()throws Exception{
  var pool=Executors.newFixedThreadPool(8);
  try{var jobs=new ArrayList<Callable<String>>();for(int i=0;i<24;i++)jobs.add(()->service.getParticipantById(id).getName());
   for(var f:pool.invokeAll(jobs))assertEquals("Cache fixture",f.get(5,TimeUnit.SECONDS));
  }finally{pool.shutdownNow();}
  verify(participants,times(1)).findById(id);
 }
 @Test void entryAndListWeightLimitsAreEnforced(){
  for(int i=0;i<3;i++){var p=participants.save(new Participant("bounded "+i,"Banda"));service.getParticipantById(p.getParticipantId());}
  assertTrue(nativeCache(CatalogueCacheConfiguration.DETAILS).getNativeCache().estimatedSize()<=2);
  assertTrue(service.getAllParticipants().size()>2);
  assertEquals(0,nativeCache(CatalogueCacheConfiguration.LIST).getNativeCache().estimatedSize());
 }
 @Test void newInstanceStartsColdAgainstSameDatabase(){
  service.getParticipantById(id);clearInvocations(participants);
  var local=(SimpleCacheManager)new CatalogueCacheConfiguration().catalogueCacheManager(ticker,30,2,2);local.afterPropertiesSet();
  assertEquals(0,((CaffeineCache)local.getCache(CatalogueCacheConfiguration.DETAILS)).getNativeCache().estimatedSize());
  assertEquals("Cache fixture",new PublicCatalogueCache(participants,local,transactions).find(id).name());verify(participants,times(1)).findById(id);
 }
 @Test void invalidLimitsFailAtStartup(){
  var cfg=new CatalogueCacheConfiguration();assertThrows(IllegalArgumentException.class,()->cfg.catalogueCacheManager(ticker,0,2,2));
  assertThrows(IllegalArgumentException.class,()->cfg.catalogueCacheManager(ticker,30,0,2));
 }
}
