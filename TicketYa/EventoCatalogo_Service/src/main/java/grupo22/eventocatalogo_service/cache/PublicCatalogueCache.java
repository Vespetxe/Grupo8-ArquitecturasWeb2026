package grupo22.eventocatalogo_service.cache;

import grupo22.eventocatalogo_service.domain.Participant;
import grupo22.eventocatalogo_service.exception.ResourceNotFoundException;
import grupo22.eventocatalogo_service.repository.ParticipantRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PublicCatalogueCache {
    public record ParticipantView(Long participantId, String name, String role) {}
    private record Key(long epoch, Long id) {}

    private final AtomicLong epoch = new AtomicLong();
    private final ParticipantRepository participants;
    private final Cache details;
    private final Cache lists;
    private final TransactionTemplate freshRead;

    public PublicCatalogueCache(ParticipantRepository participants,
            @Qualifier("catalogueCacheManager") CacheManager manager,
            PlatformTransactionManager transactions) {
        this.participants = participants;
        details = Objects.requireNonNull(manager.getCache(CatalogueCacheConfiguration.DETAILS));
        lists = Objects.requireNonNull(manager.getCache(CatalogueCacheConfiguration.LIST));
        freshRead = new TransactionTemplate(transactions);
        freshRead.setReadOnly(true);
        freshRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private ParticipantView view(Participant participant) {
        return new ParticipantView(participant.getParticipantId(), participant.getName(), participant.getRole());
    }

    private boolean writing() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    private ParticipantView load(Long id) {
        return view(participants.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Participante no encontrado con el ID: " + id)));
    }

    @SuppressWarnings("unchecked")
    public List<ParticipantView> all() {
        // Never publish an uncommitted entity, nor reuse a stale outer read transaction.
        if (writing()) return participants.findAll().stream().map(this::view).toList();
        return (List<ParticipantView>) cached(lists, epoch.get(), () -> freshRead.execute(status ->
                participants.findAll().stream().map(this::view).toList()));
    }

    public ParticipantView find(Long id) {
        if (writing()) return load(id);
        return (ParticipantView) cached(details, new Key(epoch.get(), id), () ->
                freshRead.execute(status -> load(id)));
    }

    private Object cached(Cache cache, Object key, Callable<Object> loader) {
        try {
            return cache.get(key, loader);
        } catch (Cache.ValueRetrievalException failure) {
            // Preserve the existing 404 contract; cache errors must not wrap domain exceptions.
            if (failure.getCause() instanceof RuntimeException actual) throw actual;
            throw new IllegalStateException("Catalogue cache load failed");
        } finally {
            ((CaffeineCache) cache).getNativeCache().cleanUp();
        }
    }

    public void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidate(); }
            });
        } else {
            invalidate();
        }
    }

    private void invalidate() {
        // An in-flight old loader may finish after clear; its key is no longer reachable.
        epoch.incrementAndGet();
        details.clear();
        lists.clear();
    }
}
