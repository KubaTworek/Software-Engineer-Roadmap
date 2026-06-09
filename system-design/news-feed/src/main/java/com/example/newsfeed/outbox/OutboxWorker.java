package com.example.newsfeed.outbox;

import com.example.newsfeed.feed.FeedCacheService;
import com.example.newsfeed.feedinbox.FeedInboxService;
import com.example.newsfeed.stats.PostStatsProjectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Worker przetwarzający eventy zapisane w outboxie.
 *
 * Outbox pattern rozwiązuje problem spójności między:
 * - zapisem danych biznesowych,
 * - a późniejszym wykonaniem efektów ubocznych.
 *
 * Przykład:
 * użytkownik tworzy post.
 * W jednej transakcji zapisujemy:
 * - post,
 * - event POST_CREATED w tabeli outbox.
 *
 * Potem OutboxWorker asynchronicznie pobiera event i wykonuje skutki uboczne:
 * - fan-out do feedów,
 * - odświeżenie statystyk,
 * - invalidację cache.
 *
 * Dzięki temu nie tracimy eventu, nawet jeśli aplikacja padnie
 * zaraz po zapisaniu posta.
 */
@Component
public class OutboxWorker {

    /**
     * Repozytorium eventów domenowych zapisanych w outboxie.
     *
     * To jest źródło eventów do przetworzenia.
     */
    private final DomainEventRepository domainEventRepository;

    /**
     * Repozytorium eventów już przetworzonych.
     *
     * Chroni przed ponownym wykonaniem skutków ubocznych,
     * jeśli ten sam event zostanie podjęty drugi raz.
     */
    private final ProcessedEventRepository processedEventRepository;

    /**
     * Serwis odpowiedzialny za aktualizację feed inbox.
     *
     * OutboxWorker używa go m.in. do:
     * - fan-outu po utworzeniu posta,
     * - usunięcia posta z feedów po delete.
     */
    private final FeedInboxService feedInboxService;

    /**
     * Serwis projekcji statystyk posta.
     *
     * Aktualizuje wartości typu:
     * - liczba lajków,
     * - liczba komentarzy,
     * - inne liczniki widoczne przy poście.
     */
    private final PostStatsProjectionService postStatsProjectionService;

    /**
     * Serwis cache feedu.
     *
     * Po zmianach w postach trzeba unieważnić cache,
     * żeby użytkownicy nie widzieli nieaktualnych danych.
     */
    private final FeedCacheService feedCacheService;

    /**
     * ObjectMapper do parsowania payloadu eventu.
     *
     * Payload jest zapisany jako JSON string w tabeli outbox.
     */
    private final ObjectMapper objectMapper;

    /**
     * Maksymalna liczba eventów pobieranych w jednym przebiegu workera.
     *
     * Konfiguracja:
     *
     * newsfeed:
     *   workers:
     *     outbox:
     *       batch-size: 50
     *
     * Batch chroni aplikację przed zbyt dużą ilością pracy w jednym cyklu.
     */
    private final int batchSize;

    /**
     * Wstrzyknięcie zależności workera outbox.
     */
    public OutboxWorker(
            DomainEventRepository domainEventRepository,
            ProcessedEventRepository processedEventRepository,
            FeedInboxService feedInboxService,
            PostStatsProjectionService postStatsProjectionService,
            FeedCacheService feedCacheService,
            ObjectMapper objectMapper,
            @Value("${newsfeed.workers.outbox.batch-size:50}") int batchSize
    ) {
        this.domainEventRepository = domainEventRepository;
        this.processedEventRepository = processedEventRepository;
        this.feedInboxService = feedInboxService;
        this.postStatsProjectionService = postStatsProjectionService;
        this.feedCacheService = feedCacheService;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
    }

    /**
     * Cyklicznie pobiera eventy gotowe do przetworzenia.
     *
     * fixedDelay oznacza:
     * kolejny przebieg startuje dopiero po zakończeniu poprzedniego
     * i odczekaniu wskazanej liczby milisekund.
     *
     * Dzięki temu worker nie odpala wielu przebiegów równolegle
     * w ramach jednej instancji aplikacji.
     */
    @Scheduled(fixedDelayString = "${newsfeed.workers.outbox.fixed-delay-ms:1000}")
    public void processDueEvents() {
        /*
         * Pobieramy ograniczoną paczkę eventów z outboxa.
         *
         * findDueEvents powinno zwracać eventy:
         * - nieprzetworzone,
         * - gotowe do retry,
         * - posortowane np. po createdAt / nextAttemptAt.
         */
        List<DomainEvent> events = domainEventRepository.findDueEvents(batchSize);

        /*
         * Każdy event przetwarzamy osobno.
         *
         * processOne ma własną transakcję, więc błąd jednego eventu
         * nie musi blokować całej paczki.
         */
        for (DomainEvent event : events) {
            processOne(event.getId());
        }
    }

    /**
     * Przetwarza pojedynczy event z outboxa.
     *
     * Całość działa w transakcji:
     * - odczyt eventu,
     * - sprawdzenie idempotencji,
     * - wykonanie handlera,
     * - zapis ProcessedEvent,
     * - oznaczenie eventu jako processed albo failed.
     */
    @Transactional
    public void processOne(UUID eventId) {
        /*
         * Pobieramy event po ID.
         *
         * Robimy to ponownie, mimo że processDueEvents ma już event,
         * żeby mieć świeżą encję zarządzaną przez aktualną transakcję.
         */
        DomainEvent event = domainEventRepository.findById(eventId)
                .orElseThrow();

        /*
         * Idempotencja.
         *
         * Jeśli event jest już w processed_events, to skutki uboczne
         * zostały wcześniej wykonane.
         *
         * Nie wykonujemy handle() ponownie.
         */
        if (processedEventRepository.existsById(event.getId())) {
            event.markProcessed();
            domainEventRepository.save(event);
            return;
        }

        try {
            /*
             * Wykonujemy logikę zależną od typu eventu.
             */
            handle(event);

            /*
             * Zapisujemy informację, że event został przetworzony.
             *
             * To jest kluczowe dla exactly-once effect na poziomie aplikacji:
             * nawet jeśli worker podejmie event drugi raz,
             * processed_events zatrzyma powtórne skutki uboczne.
             */
            processedEventRepository.save(
                    new ProcessedEvent(
                            event.getId(),
                            Instant.now()
                    )
            );

            /*
             * Oznaczamy event w outboxie jako przetworzony.
             */
            event.markProcessed();
            domainEventRepository.save(event);
        } catch (Exception exception) {
            /*
             * Jeśli handler rzuci wyjątek, event nie trafia do processed_events.
             *
             * Dzięki temu może zostać podjęty ponownie przez retry.
             *
             * markFailed powinno zapisać:
             * - liczbę prób,
             * - ostatni błąd,
             * - nextAttemptAt,
             * - status FAILED / RETRYABLE.
             */
            event.markFailed(exception);
            domainEventRepository.save(event);
        }
    }

    /**
     * Wykonuje właściwą logikę biznesową dla eventu.
     *
     * Event payload jest JSON-em, więc najpierw parsujemy go do JsonNode,
     * a potem wyciągamy wymagane pola zależnie od typu eventu.
     */
    private void handle(DomainEvent event) throws Exception {
        /*
         * Payload eventu jest przechowywany jako String.
         *
         * Przykład:
         * {
         *   "postId": "...",
         *   "authorId": "...",
         *   "createdAt": "..."
         * }
         */
        JsonNode payload = objectMapper.readTree(event.getPayload());

        /*
         * Routing eventu po typie.
         *
         * Każdy typ eventu uruchamia inne efekty uboczne.
         */
        switch (event.getEventType()) {
            case DomainEventPublisher.POST_CREATED -> {
                /*
                 * POST_CREATED oznacza, że nowy post został zapisany w bazie.
                 *
                 * Teraz trzeba:
                 * - rozprowadzić go do feedów,
                 * - utworzyć / odświeżyć statystyki,
                 * - wyczyścić globalny cache feedu.
                 */
                UUID postId = uuid(payload, "postId");
                UUID authorId = uuid(payload, "authorId");
                Instant createdAt = Instant.parse(
                        payload.get("createdAt").asText()
                );

                /*
                 * Fan-out posta do feed inbox.
                 *
                 * To sprawia, że followersi autora zobaczą post w feedzie.
                 */
                feedInboxService.fanoutPostCreated(
                        postId,
                        authorId,
                        createdAt
                );

                /*
                 * Odświeżamy projekcję statystyk posta.
                 *
                 * Dla nowego posta zwykle oznacza to utworzenie liczników z zerami.
                 */
                postStatsProjectionService.refreshStats(postId);

                /*
                 * Globalny feed może zawierać najnowsze/trending posty.
                 *
                 * Po dodaniu nowego posta trzeba unieważnić cache,
                 * żeby kolejne requesty mogły zobaczyć świeżą zawartość.
                 */
                feedCacheService.evictGlobalFeed();
            }

            case DomainEventPublisher.POST_DELETED -> {
                /*
                 * POST_DELETED oznacza, że post został usunięty logicznie
                 * albo fizycznie.
                 *
                 * Trzeba usunąć jego referencje z feedów
                 * i wyczyścić cache globalnego feedu.
                 */
                UUID postId = uuid(payload, "postId");

                /*
                 * Usuwamy post z feed inbox użytkowników.
                 *
                 * Dzięki temu usunięty post nie będzie dalej pojawiał się w feedzie.
                 */
                feedInboxService.removePostFromFeeds(postId);

                /*
                 * Czyścimy globalny cache, bo mógł zawierać usunięty post.
                 */
                feedCacheService.evictGlobalFeed();
            }

            case DomainEventPublisher.POST_LIKED,
                 DomainEventPublisher.POST_UNLIKED,
                 DomainEventPublisher.COMMENT_CREATED,
                 DomainEventPublisher.COMMENT_DELETED -> {
                /*
                 * Te eventy nie zmieniają dystrybucji posta w feedzie.
                 *
                 * Zmieniają jednak statystyki widoczne przy poście:
                 * - liczba lajków,
                 * - liczba komentarzy.
                 *
                 * Dlatego odświeżamy projekcję statystyk.
                 */
                UUID postId = uuid(payload, "postId");

                postStatsProjectionService.refreshStats(postId);
            }

            default -> {
                /*
                 * Nieznany typ eventu traktujemy jako błąd.
                 *
                 * Event zostanie oznaczony jako failed,
                 * żeby problem był widoczny i nie został cicho zignorowany.
                 */
                throw new IllegalArgumentException(
                        "Unsupported event type: " + event.getEventType()
                );
            }
        }
    }

    /**
     * Pomocniczo wyciąga UUID z payloadu JSON.
     *
     * Zakładamy, że pole istnieje i jest poprawnym UUID.
     * Jeśli nie, poleci wyjątek i event zostanie oznaczony jako failed.
     */
    private UUID uuid(JsonNode payload, String field) {
        return UUID.fromString(
                payload.get(field).asText()
        );
    }
}