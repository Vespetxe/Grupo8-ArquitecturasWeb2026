package grupo22.eventocatalogo_service.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class CatalogueCacheConfiguration {
    public static final String DETAILS = "catalogueParticipantById";
    public static final String LIST = "catalogueParticipantList";

    @Bean
    public Ticker catalogueTicker() {
        return Ticker.systemTicker();
    }

    @Bean
    public CacheManager catalogueCacheManager(Ticker catalogueTicker,
            @Value("${ticketya.catalogue-cache.ttl-seconds:30}") long ttl,
            @Value("${ticketya.catalogue-cache.max-participants:512}") int maximum,
            @Value("${ticketya.catalogue-cache.list-max-items:200}") int listMaximum) {
        if (ttl < 1 || ttl > 300 || maximum < 1 || maximum > 10000 || listMaximum < 1 || listMaximum > 1000) {
            throw new IllegalArgumentException("Invalid catalogue cache limits");
        }
        var details = new CaffeineCache(DETAILS, Caffeine.newBuilder()
                .maximumSize(maximum).expireAfterWrite(Duration.ofSeconds(ttl))
                .ticker(catalogueTicker).recordStats().build(), false);
        // Bound retained list data, rather than merely counting its single key.
        var lists = new CaffeineCache(LIST, Caffeine.newBuilder().maximumWeight(listMaximum)
                .weigher((Object key, Object value) -> Math.max(1, ((List<?>) value).size()))
                .expireAfterWrite(Duration.ofSeconds(ttl)).ticker(catalogueTicker).recordStats().build(), false);
        var manager = new SimpleCacheManager();
        manager.setCaches(List.of(details, lists));
        return manager;
    }
}
