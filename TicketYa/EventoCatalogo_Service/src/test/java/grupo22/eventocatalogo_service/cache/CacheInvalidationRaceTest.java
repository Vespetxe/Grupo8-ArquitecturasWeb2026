package grupo22.eventocatalogo_service.cache;

import grupo22.eventocatalogo_service.domain.Participant;
import grupo22.eventocatalogo_service.repository.ParticipantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CacheInvalidationRaceTest {
 @Test void lateLoaderCannotRefillTheCurrentGeneration()throws Exception{
  var repository=mock(ParticipantRepository.class);
  var tx=mock(PlatformTransactionManager.class);when(tx.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
  var config=new CatalogueCacheConfiguration();var original=(SimpleCacheManager)config.catalogueCacheManager(()->0L,30,10,10);original.afterPropertiesSet();
  var detail=spy((CaffeineCache)original.getCache(CatalogueCacheConfiguration.DETAILS));
  // Model a loader that completes after eviction, without relying on Caffeine's lock timing.
  doNothing().when(detail).clear();
  var manager=new SimpleCacheManager();manager.setCaches(List.of(detail,original.getCache(CatalogueCacheConfiguration.LIST)));manager.afterPropertiesSet();
  var cache=new PublicCatalogueCache(repository,manager,tx);
  var entered=new CountDownLatch(1);var finish=new CountDownLatch(1);var first=new AtomicBoolean(true);
  when(repository.findById(1L)).thenAnswer(i->{var p=new Participant(first.get()?"old":"new","Banda");p.setParticipantId(1L);
   if(first.getAndSet(false)){entered.countDown();assertTrue(finish.await(5,TimeUnit.SECONDS));}return Optional.of(p);});
  var pool=Executors.newSingleThreadExecutor();
  try{var stale=pool.submit(()->cache.find(1L));assertTrue(entered.await(5,TimeUnit.SECONDS));cache.invalidateAfterCommit();finish.countDown();assertEquals("old",stale.get(5,TimeUnit.SECONDS).name());assertEquals("new",cache.find(1L).name());verify(repository,times(2)).findById(1L);}
  finally{finish.countDown();pool.shutdownNow();}
 }
}
