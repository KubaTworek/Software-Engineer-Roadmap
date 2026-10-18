package pl.jakubtworek.backend.catalog.application;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend.catalog.cache.RedisJsonCache;
import pl.jakubtworek.backend.catalog.chaos.CatalogChaosSettings;
import pl.jakubtworek.backend.catalog.domain.EventEntity;
import pl.jakubtworek.backend.catalog.repository.EventRepository;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cache może pokazać wartość orientacyjną, lecz kontrakt availability musi zachować
 * zarówno całkowitą pojemność, jak i bieżący snapshot. Ostateczny invariant nadal
 * należy do Reservation Service.
 */
class EventCatalogServiceTest {

    @Test
    void availabilityReadModelKeepsTotalCapacityAndCurrentSnapshot() {
        UUID eventId = UUID.randomUUID();
        EventRepository repository = mock(EventRepository.class);
        RedisJsonCache cache = mock(RedisJsonCache.class);
        when(repository.findById(eventId)).thenReturn(Optional.of(new EventEntity(
                eventId, "Conference", "Warsaw", Instant.parse("2027-01-10T10:00:00Z"), 100, 37)));
        when(cache.getOrLoad(anyString(), anyString(), any(Duration.class), any(JavaType.class), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());

        EventCatalogService service = new EventCatalogService(repository, cache, new ObjectMapper(),
                Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofSeconds(10),
                new CatalogChaosSettings());

        var result = service.getAvailability(eventId);

        assertThat(result.totalTickets()).isEqualTo(100);
        assertThat(result.availableTickets()).isEqualTo(37);
    }
}
