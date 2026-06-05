package pl.jakubtworek.backend.catalog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.jakubtworek.backend.catalog.api.AvailabilityResponse;
import pl.jakubtworek.backend.catalog.api.EventResponse;
import pl.jakubtworek.backend.catalog.cache.RedisJsonCache;
import pl.jakubtworek.backend.catalog.chaos.CatalogChaosSettings;
import pl.jakubtworek.backend.catalog.domain.EventEntity;
import pl.jakubtworek.backend.catalog.repository.EventRepository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Warstwa aplikacyjna katalogu wydarzeń.
 *
 * Ten serwis odpowiada za operacje read-side:
 *
 * - pobranie listy wydarzeń,
 * - pobranie szczegółów wydarzenia,
 * - pobranie dostępności biletów.
 *
 * Celowo znajduje się tu logika cache'owania, ponieważ Catalog Service jest typowym
 * serwisem o dużym udziale odczytów. W scenariuszach load testingu endpointy katalogowe
 * powinny dobrze pokazywać wpływ cache na latency, throughput i obciążenie bazy danych.
 */
@Service
public class EventCatalogService {

    private static final Logger log = LoggerFactory.getLogger(EventCatalogService.class);

    /**
     * Repozytorium JPA do odczytu wydarzeń z PostgreSQL.
     *
     * Baza danych jest źródłem prawdy. Redis jest tylko cachem i może zostać utracony
     * bez utraty danych biznesowych.
     */
    private final EventRepository eventRepository;

    /**
     * Cache JSON oparty o Redis.
     *
     * Cache przechowuje gotowe DTO odpowiedzi, a nie encje JPA. Dzięki temu:
     *
     * - ograniczamy liczbę zapytań do bazy,
     * - skracamy czas odpowiedzi,
     * - nie wystawiamy modelu persistence jako kontraktu cache.
     */
    private final RedisJsonCache cache;

    /**
     * ObjectMapper jest przekazywany do cache, żeby poprawnie konstruować typy Java
     * dla deserializacji JSON-a z Redisa.
     *
     * Jest to szczególnie ważne dla list, np. List<EventResponse>, bo samo List.class
     * nie zawiera informacji o typie elementów.
     */
    private final ObjectMapper objectMapper;

    /**
     * TTL dla listy wydarzeń.
     *
     * Lista wydarzeń zwykle nie musi być odświeżana co request. Można ją cache'ować
     * relatywnie krótko, np. 60 sekund, żeby znacząco zmniejszyć liczbę odczytów z DB.
     */
    private final Duration eventListTtl;

    /**
     * TTL dla szczegółów pojedynczego wydarzenia.
     *
     * Szczegóły wydarzenia zmieniają się rzadziej niż dostępność biletów, więc mogą mieć
     * dłuższy TTL niż availability.
     */
    private final Duration eventDetailsTtl;

    /**
     * TTL dla dostępności biletów.
     *
     * Dostępność jest bardziej dynamiczna niż nazwa wydarzenia czy venue, więc TTL powinien
     * być krótszy. To jest klasyczny trade-off:
     *
     * - dłuższy TTL = mniej zapytań do DB, lepsza wydajność,
     * - krótszy TTL = świeższe dane, większe obciążenie DB.
     */
    private final Duration availabilityTtl;

    /**
     * Ustawienia chaos/fault injection dla Catalog Service.
     *
     * Pozwala sztucznie spowolnić dostęp do bazy, żeby w Fazie 5 sprawdzać:
     *
     * - wzrost latency,
     * - zachowanie cache,
     * - alerty,
     * - trace'y,
     * - wpływ wolnego downstreamu na cały system.
     */
    private final CatalogChaosSettings chaosSettings;

    public EventCatalogService(EventRepository eventRepository,
                               RedisJsonCache cache,
                               ObjectMapper objectMapper,
                               @Value("${app.cache.events.ttl:60s}") Duration eventListTtl,
                               @Value("${app.cache.event-details.ttl:120s}") Duration eventDetailsTtl,
                               @Value("${app.cache.availability.ttl:10s}") Duration availabilityTtl,
                               CatalogChaosSettings chaosSettings) {
        this.eventRepository = eventRepository;
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.eventListTtl = eventListTtl;
        this.eventDetailsTtl = eventDetailsTtl;
        this.availabilityTtl = availabilityTtl;
        this.chaosSettings = chaosSettings;
    }

    /**
     * Zwraca listę wydarzeń.
     *
     * Cache key:
     *
     * - namespace: "events"
     * - key: "all"
     *
     * Jeśli dane są w Redisie, odpowiedź powinna ominąć bazę danych.
     * Jeśli nie ma ich w Redisie, loader pobiera dane z PostgreSQL i zapisuje wynik w cache.
     */
    public List<EventResponse> listEvents() {
        return cache.getOrLoad(
                "events",
                "all",
                eventListTtl,
                objectMapper.getTypeFactory().constructCollectionType(List.class, EventResponse.class),
                () -> {
                    /*
                     * Delay jest aplikowany tylko przy realnym odczycie z DB.
                     *
                     * Jeśli odpowiedź pochodzi z cache, chaos DB delay nie powinien wpływać
                     * na czas odpowiedzi. To pozwala dobrze zobaczyć ochronną rolę cache.
                     */
                    applyDatabaseDelayIfConfigured();

                    return eventRepository.findAll()
                            .stream()
                            .map(this::toResponse)
                            .toList();
                }
        );
    }

    /**
     * Zwraca szczegóły pojedynczego wydarzenia.
     *
     * Ten endpoint ma dłuższy TTL niż availability, ponieważ dane opisowe wydarzenia
     * zwykle zmieniają się rzadziej niż liczba dostępnych biletów.
     */
    public EventResponse getEvent(UUID id) {
        return cache.getOrLoad(
                "event-details",
                id.toString(),
                eventDetailsTtl,
                objectMapper.getTypeFactory().constructType(EventResponse.class),
                () -> {
                    applyDatabaseDelayIfConfigured();

                    return eventRepository.findById(id)
                            .map(this::toResponse)
                            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + id));
                }
        );
    }

    /**
     * Zwraca aktualną dostępność biletów dla wydarzenia.
     *
     * Ten endpoint ma najkrótszy TTL, bo availability jest najbardziej wrażliwe na świeżość.
     *
     * Uwaga projektowa:
     * To jest dobre do szybkiego pokazywania dostępności użytkownikowi, ale nie powinno być
     * jedynym mechanizmem ochrony przed oversellingiem. Ostateczna kontrola dostępności
     * musi być wykonana w Reservation Service przy tworzeniu rezerwacji.
     */
    public AvailabilityResponse getAvailability(UUID id) {
        return cache.getOrLoad(
                "event-availability",
                id.toString(),
                availabilityTtl,
                objectMapper.getTypeFactory().constructType(AvailabilityResponse.class),
                () -> {
                    applyDatabaseDelayIfConfigured();

                    EventEntity event = eventRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + id));

                    return new AvailabilityResponse(event.getId(), event.getAvailableTickets());
                }
        );
    }

    /**
     * Sztucznie opóźnia dostęp do bazy danych, jeśli chaos mode jest włączony.
     *
     * To nie jest logika produkcyjna. To narzędzie treningowe do testów:
     *
     * - db-slow,
     * - cache effectiveness,
     * - tracing latency,
     * - alertów na p95/p99,
     * - obserwacji, czy cache zmniejsza wpływ wolnej bazy.
     */
    private void applyDatabaseDelayIfConfigured() {
        long delayMs = chaosSettings.databaseDelayMs();

        if (delayMs <= 0) {
            return;
        }

        try {
            log.warn("catalog_database_delay_simulated delayMs={}", delayMs);
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            /*
             * Przy InterruptedException zawsze przywracamy flagę interrupted.
             *
             * To pozwala wyższym warstwom / executorom poprawnie zauważyć, że wątek został
             * przerwany. Samo połknięcie InterruptedException byłoby błędem.
             */
            Thread.currentThread().interrupt();

            throw new IllegalStateException("Interrupted while simulating catalog database delay", e);
        }
    }

    /**
     * Mapuje encję JPA na DTO odpowiedzi API.
     *
     * Nie zwracamy EventEntity bezpośrednio z API, żeby nie mieszać modelu persistence
     * z kontraktem HTTP. DTO daje kontrolę nad tym, co wystawiamy klientowi.
     */
    private EventResponse toResponse(EventEntity event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getVenue(),
                event.getStartsAt(),
                event.getTotalTickets(),
                event.getAvailableTickets()
        );
    }
}