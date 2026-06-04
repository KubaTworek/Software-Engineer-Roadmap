package pl.jakubtworek.marketplace.integration.kafka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEvent;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventRepository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventStatus;

import java.util.UUID;

/**
 * Worker publikujący trwałe eventy z outboxa do topiców Kafki.
 *
 * Ta klasa reprezentuje etap 4 architektury:
 * - event jest najpierw zapisywany do tabeli outbox_events,
 * - następnie KafkaOutboxWorker pobiera event z outboxa,
 * - mapuje go na KafkaEnvelope,
 * - wybiera odpowiedni topic,
 * - publikuje wiadomość do Kafki,
 * - oznacza event jako PUBLISHED albo FAILED.
 *
 * Ten worker zastępuje wcześniejszy OutboxWorker z fazy 3 w realnym asynchronicznym flow.
 * Faza 3 publikowała eventy lokalnie do ApplicationEventBus.
 * Faza 4 publikuje eventy do brokera, żeby konsumenci mogli je przetwarzać asynchronicznie.
 */
@Component
public class KafkaOutboxWorker {

    /**
     * Domyślna liczba eventów pobieranych w jednym przebiegu workera.
     *
     * Batch ogranicza długość jednej transakcji oraz ilość pracy wykonywanej w jednym cyklu.
     */
    private static final int DEFAULT_BATCH_SIZE = 50;

    /**
     * Repozytorium outboxa.
     *
     * Worker pobiera z niego eventy o statusie NEW albo FAILED,
     * a potem oznacza je jako PUBLISHED lub FAILED.
     */
    private final OutboxEventRepository repository;

    /**
     * Port publikowania wiadomości do Kafki.
     *
     * Worker nie zna szczegółów konkretnej implementacji klienta Kafki.
     * Może to być prawdziwy adapter Spring Kafka albo broker in-memory w testach.
     */
    private final KafkaMessagePublisher publisher;

    /**
     * Resolver topiców.
     *
     * Decyduje, do którego topicu powinien trafić event na podstawie jego typu.
     * Przykład:
     * - OrderPlaced -> marketplace.order-events.v1,
     * - PaymentReserved -> marketplace.payment-events.v1,
     * - StockReserved -> marketplace.inventory-events.v1.
     */
    private final KafkaTopicResolver topicResolver;

    /**
     * Mapper zamieniający OutboxEvent na KafkaEnvelope.
     *
     * KafkaEnvelope jest technicznym kontraktem wiadomości wysyłanej na broker.
     */
    private final KafkaEnvelopeMapper envelopeMapper;

    /**
     * Flaga sterująca cyklicznym uruchamianiem workera.
     *
     * Przydatne w testach integracyjnych, gdzie często chcemy ręcznie wywoływać
     * publishNew(...) albo publishUntilIdle(...), zamiast pozwalać schedulerowi działać
     * w tle.
     *
     * Konfiguracja:
     * marketplace.kafka-outbox.scheduled-enabled=false
     */
    @Value("${marketplace.kafka-outbox.scheduled-enabled:true}")
    private boolean scheduledEnabled;

    /**
     * Konstruktor używany przez Springa.
     *
     * Wszystkie zależności są wstrzykiwane jako beany.
     * Nie tworzymy ręcznie KafkaTopicResolver ani KafkaEnvelopeMapper, żeby uniknąć
     * niespójności konfiguracji i problemów z testowaniem.
     */
    @Autowired
    public KafkaOutboxWorker(
            OutboxEventRepository repository,
            KafkaMessagePublisher publisher,
            KafkaTopicResolver topicResolver,
            KafkaEnvelopeMapper envelopeMapper
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.topicResolver = topicResolver;
        this.envelopeMapper = envelopeMapper;
    }

    public KafkaOutboxWorker(OutboxEventRepository repository, KafkaMessagePublisher publisher) {
        this(repository, publisher, new KafkaTopicResolver(), new KafkaEnvelopeMapper());
    }

    /**
     * Cyklicznie publikuje nowe eventy z outboxa do Kafki.
     *
     * fixedDelay oznacza, że kolejny przebieg rozpocznie się dopiero po zakończeniu
     * poprzedniego i odczekaniu wskazanego czasu.
     *
     * Jeśli scheduledEnabled=false, metoda nic nie robi.
     */
    @Scheduled(fixedDelayString = "${marketplace.kafka-outbox.worker-delay-ms:5000}")
    public void scheduledPublish() {
        if (!scheduledEnabled) {
            return;
        }

        publishNew(DEFAULT_BATCH_SIZE);
    }

    /**
     * Publikuje eventy o statusie NEW.
     *
     * Zwraca liczbę pobranych eventów, niekoniecznie liczbę skutecznie opublikowanych.
     * Jeżeli publikacja pojedynczego eventu się nie powiedzie, event zostanie oznaczony
     * jako FAILED, a worker przejdzie do kolejnego eventu.
     */
    @Transactional
    public int publishNew(int limit) {
        var events = repository.findNew(limit);

        events.forEach(this::publishOneSafely);

        return events.size();
    }

    /**
     * Ponawia publikację eventów oznaczonych jako FAILED.
     *
     * To prosta forma retry na poziomie outboxa.
     * Produkcyjnie warto dodać backoff, limit maksymalnej liczby prób i nextAttemptAt.
     */
    @Transactional
    public int retryFailed(int limit) {
        var events = repository.findFailed(limit);

        events.forEach(this::publishOneSafely);

        return events.size();
    }

    /**
     * Ręcznie ponawia publikację konkretnego eventu.
     *
     * Najpierw oznacza event jako NEW, a następnie próbuje go opublikować.
     * Używane przez endpoint administracyjny.
     */
    @Transactional
    public void retryManually(UUID outboxEventId) {
        repository.markNewForRetry(outboxEventId);
        publishById(outboxEventId);
    }

    /**
     * Publikuje konkretny event z outboxa po ID.
     *
     * Jeśli event został już opublikowany, metoda nic nie robi.
     * Chroni to przed prostym przypadkiem ręcznego zdublowania publikacji.
     */
    @Transactional
    public void publishById(UUID outboxEventId) {
        var event = repository.findById(outboxEventId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Outbox event not found: " + outboxEventId
                ));

        if (event.status() == OutboxEventStatus.PUBLISHED) {
            return;
        }

        publishOneSafely(event);
    }

    /**
     * Pomocnicza metoda testowa/developerska.
     *
     * Publikuje eventy aż do momentu, gdy outbox będzie pusty,
     * albo do osiągnięcia limitu iteracji.
     *
     * Jest to przydatne, bo przetworzenie jednego eventu może wygenerować kolejne eventy.
     * Przykład:
     * - OrderPlaced trafia do Kafki,
     * - consumer Payment generuje PaymentReserved,
     * - consumer Inventory generuje StockReserved,
     * - consumer Ordering może wygenerować OrderConfirmed.
     */
    public int publishUntilIdle(int batchSize, int maxIterations) {
        int total = 0;

        for (int i = 0; i < maxIterations; i++) {
            int published = publishNew(batchSize);
            total += published;

            if (published == 0) {
                return total;
            }
        }

        throw new IllegalStateException(
                "Outbox is still producing new events after " + maxIterations + " iterations"
        );
    }

    /**
     * Próbuje opublikować pojedynczy event do Kafki.
     *
     * Jeśli publikacja się powiedzie:
     * - event zostaje oznaczony jako PUBLISHED.
     *
     * Jeśli publikacja się nie powiedzie:
     * - event zostaje oznaczony jako FAILED,
     * - w outboxie zapisywany jest komunikat błędu.
     *
     * Metoda celowo łapie wyjątek, żeby jeden uszkodzony event nie zatrzymał całego batcha.
     */
    private void publishOneSafely(OutboxEvent event) {
        try {
            var topic = topicResolver.resolve(event.eventType()).topicName();

            publisher.publish(
                    topic,
                    event.aggregateId().toString(),
                    envelopeMapper.toEnvelope(event)
            );

            repository.markPublished(event.id());
        } catch (Exception e) {
            repository.markFailed(event.id(), e.getMessage());
        }
    }
}