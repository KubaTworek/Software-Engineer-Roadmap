package pl.jakubtworek.marketplace.integration.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventMapper;
import pl.jakubtworek.marketplace.shared.events.ApplicationEventBus;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import pl.jakubtworek.marketplace.shared.observability.InMemoryFlowTraceRepository;
import pl.jakubtworek.marketplace.shared.observability.MarketplaceMetrics;
import pl.jakubtworek.marketplace.shared.observability.ObservabilityService;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Konsument wiadomości kafkowych używany w testach oraz w architekturze fazy 4/6.
 *
 * Ta klasa symuluje logikę prawdziwego konsumenta Kafki:
 * - pobiera rekordy z brokera,
 * - deserializuje envelope do eventu domenowego,
 * - uruchamia lokalne handlery przez ApplicationEventBus,
 * - zapisuje event jako przetworzony w processed_events,
 * - commituje offset dopiero po skutecznym przetworzeniu,
 * - obsługuje retry,
 * - wysyła błędne eventy do DLQ,
 * - aktualizuje metryki i trace observability.
 *
 * W prawdziwej integracji ze Spring Kafka metoda processRecord(...) mogłaby być wywoływana
 * z @KafkaListener skonfigurowanego z manualnym ack/commit.
 *
 * Najważniejsza zasada:
 * receive -> process -> mark processed -> commit offset -> update observability.
 */
public class KafkaConsumerWorker {

    /**
     * Nazwa konkretnego konsumenta.
     *
     * Używana w processed_events oraz observability.
     * Dzięki temu ten sam event może być przetworzony niezależnie przez różnych konsumentów,
     * ale nie powinien być przetworzony dwa razy przez tego samego konsumenta.
     */
    private final String consumerName;

    /**
     * Topic, z którego ten worker pobiera wiadomości.
     */
    private final String topic;

    /**
     * Consumer group.
     *
     * Offsety są commitowane per topic + consumer group.
     */
    private final String consumerGroup;

    /**
     * Abstrakcja brokera Kafki.
     *
     * W testach może to być broker in-memory.
     * Produkcyjnie tę rolę pełniłby adapter na prawdziwą Kafkę.
     */
    private final KafkaMessageBroker broker;

    /**
     * Lokalny dispatcher eventów.
     *
     * Po deserializacji wiadomości kafkowej do DomainEvent, event jest przekazywany
     * do handlerów wewnątrz modularnego monolitu.
     */
    private final ApplicationEventBus eventBus;

    /**
     * Repozytorium processed_events.
     *
     * Służy do idempotencji konsumenta.
     * Przed przetworzeniem eventu sprawdzamy, czy dany eventId był już obsłużony
     * przez tego konkretnego consumerName.
     */
    private final ProcessedEventRepository processedEvents;

    /**
     * Repozytorium DLQ.
     *
     * Po przekroczeniu limitu prób event trafia do Dead Letter Queue.
     */
    private final DlqEventRepository dlqRepository;

    /**
     * Mapper KafkaEnvelope <-> OutboxEvent.
     *
     * KafkaEnvelope jest technicznym formatem wiadomości na brokerze.
     * OutboxEvent jest formatem technicznym używanym przez outbox.
     */
    private final KafkaEnvelopeMapper envelopeMapper = new KafkaEnvelopeMapper();

    /**
     * Mapper OutboxEvent -> DomainEvent.
     *
     * Używany do odtworzenia konkretnego eventu domenowego, np. OrderPlaced,
     * PaymentReserved albo StockReserved.
     */
    private final OutboxEventMapper outboxEventMapper;

    /**
     * Polityka retry.
     *
     * Określa maksymalną liczbę prób przetworzenia wiadomości.
     */
    private final RetryPolicy retryPolicy;

    /**
     * Serwis observability.
     *
     * Odpowiada za trace flow, metryki, retry count, DLQ count i consumer lag.
     */
    private final ObservabilityService observability;

    /**
     * Konstruktor pomocniczy.
     *
     * Tworzy domyślne observability oparte o implementacje in-memory.
     * Przydatne w testach, gdzie nie chcemy konfigurować pełnego observability ręcznie.
     */
    public KafkaConsumerWorker(
            String consumerName,
            String topic,
            String consumerGroup,
            KafkaMessageBroker broker,
            ApplicationEventBus eventBus,
            ProcessedEventRepository processedEvents,
            DlqEventRepository dlqRepository,
            ObjectMapper objectMapper,
            RetryPolicy retryPolicy
    ) {
        this(
                consumerName,
                topic,
                consumerGroup,
                broker,
                eventBus,
                processedEvents,
                dlqRepository,
                objectMapper,
                retryPolicy,
                new ObservabilityService(
                        new InMemoryFlowTraceRepository(),
                        new MarketplaceMetrics()
                )
        );
    }

    /**
     * Główny konstruktor.
     *
     * Pozwala jawnie wstrzyknąć ObservabilityService, co jest przydatne w testach
     * sprawdzających metryki, trace, retry i DLQ.
     */
    public KafkaConsumerWorker(
            String consumerName,
            String topic,
            String consumerGroup,
            KafkaMessageBroker broker,
            ApplicationEventBus eventBus,
            ProcessedEventRepository processedEvents,
            DlqEventRepository dlqRepository,
            ObjectMapper objectMapper,
            RetryPolicy retryPolicy,
            ObservabilityService observability
    ) {
        this.consumerName = consumerName;
        this.topic = topic;
        this.consumerGroup = consumerGroup;
        this.broker = broker;
        this.eventBus = eventBus;
        this.processedEvents = processedEvents;
        this.dlqRepository = dlqRepository;
        this.outboxEventMapper = new OutboxEventMapper(objectMapper);
        this.retryPolicy = retryPolicy;
        this.observability = observability;
    }

    /**
     * Pobiera wiadomości z brokera i przetwarza je jedna po drugiej.
     *
     * maxRecords ogranicza liczbę rekordów pobieranych w jednym przebiegu.
     *
     * Po przetworzeniu paczki aktualizowany jest consumer lag.
     *
     * Zwraca liczbę rekordów pobranych z brokera, niekoniecznie liczbę eventów
     * biznesowo przetworzonych, bo duplikaty też są liczone jako obsłużone rekordy.
     */
    public int pollAndProcess(int maxRecords) {
        int processed = 0;

        for (KafkaRecord record : broker.poll(topic, consumerGroup, maxRecords)) {
            processRecord(record);
            processed++;
        }

        recordLag();

        return processed;
    }

    /**
     * Pomocnicza metoda testowa symulująca klasyczny scenariusz awarii konsumenta.
     *
     * Scenariusz:
     * 1. Konsument odbiera event.
     * 2. Wykonuje efekt uboczny, np. zmienia stan domenowy.
     * 3. Zapisuje processed_events.
     * 4. Aplikacja pada przed commitem offsetu.
     *
     * Po restarcie broker dostarczy tę samą wiadomość ponownie, bo offset nie został
     * commitowany. Idempotencja powinna wtedy wykryć wpis w processed_events i pominąć
     * ponowne wykonanie efektu ubocznego.
     */
    public void simulateCrashAfterSuccessfulProcessingBeforeCommit(KafkaRecord record) {
        if (processedEvents.exists(record.envelope().eventId(), consumerName)) {
            throw new IllegalStateException("event was already processed before crash simulation");
        }

        observability.eventReceived(record, consumerName);

        DomainEvent event = outboxEventMapper.toDomainEvent(
                envelopeMapper.toOutboxEvent(record.envelope())
        );

        eventBus.publish(event);

        processedEvents.save(
                ProcessedEvent.processed(record.envelope().eventId(), consumerName)
        );

        observability.eventProcessed(
                event,
                consumerName,
                record.topic(),
                0
        );

        throw new SimulatedConsumerCrashException("simulated crash before offset commit");
    }

    /**
     * Przetwarza pojedynczy rekord z Kafki.
     *
     * To jest najważniejsza metoda klasy.
     *
     * Zasady:
     * - jeśli event był już przetworzony przez tego konsumenta, commitujemy offset
     *   i nie wykonujemy handlerów ponownie,
     * - jeśli event jest nowy, próbujemy go przetworzyć,
     * - po sukcesie zapisujemy processed_events i commitujemy offset,
     * - po błędzie ponawiamy do limitu retryPolicy.maxAttempts(),
     * - po przekroczeniu limitu zapisujemy event do DLQ i commitujemy offset.
     */
    public void processRecord(KafkaRecord record) {
        UUID eventId = record.envelope().eventId();

        observability.eventReceived(record, consumerName);

        /*
         * Idempotencja konsumenta.
         *
         * Jeśli event był już przetworzony przez tego konsumenta, nie wolno drugi raz
         * wykonywać efektów ubocznych. Commitujemy offset, bo rekord można uznać
         * za bezpiecznie obsłużony.
         */
        if (processedEvents.exists(eventId, consumerName)) {
            broker.commit(record.topic(), consumerGroup, record.offset());
            observability.duplicateSkipped(record, consumerName);
            recordLag();
            return;
        }

        int attempts = 0;

        while (true) {
            attempts++;

            Instant startedAt = Instant.now();

            try {
                /*
                 * Odtwarzamy DomainEvent z kafkowego envelope.
                 */
                DomainEvent event = outboxEventMapper.toDomainEvent(
                        envelopeMapper.toOutboxEvent(record.envelope())
                );

                /*
                 * Uruchamiamy lokalne handlery domenowe/aplikacyjne.
                 */
                eventBus.publish(event);

                /*
                 * Zapisujemy informację, że ten konkretny consumer przetworzył event.
                 *
                 * Ten zapis powinien być trwały, bo to on chroni przed duplikatami
                 * po awarii przed commitem offsetu.
                 */
                processedEvents.save(
                        ProcessedEvent.processed(eventId, consumerName)
                );

                /*
                 * Commit offsetu dopiero po skutecznym przetworzeniu i zapisie
                 * processed_events.
                 */
                broker.commit(record.topic(), consumerGroup, record.offset());

                observability.eventProcessed(
                        event,
                        consumerName,
                        record.topic(),
                        Duration.between(startedAt, Instant.now()).toMillis()
                );

                recordLag();

                return;
            } catch (Exception e) {
                /*
                 * Rejestrujemy próbę retry w observability.
                 */
                observability.retryScheduled(record, consumerName, attempts, e);

                /*
                 * Po przekroczeniu limitu prób zapisujemy event do DLQ.
                 *
                 * Następnie commitujemy offset, żeby ten sam wadliwy event nie blokował
                 * całej partycji w nieskończoność.
                 */
                if (attempts >= retryPolicy.maxAttempts()) {
                    DlqEvent dlqEvent = DlqEvent.newEvent(
                            record.topic(),
                            consumerGroup,
                            record.offset(),
                            record.envelope(),
                            e.getMessage(),
                            attempts
                    );

                    dlqRepository.save(dlqEvent);

                    observability.sentToDlq(dlqEvent);

                    broker.commit(record.topic(), consumerGroup, record.offset());

                    recordLag();

                    return;
                }
            }
        }
    }

    /**
     * Oblicza aktualny lag konsumenta.
     *
     * Lag = endOffset - committedOffset.
     *
     * Jeśli wynik byłby ujemny, zwracamy 0, bo lag nie powinien być ujemny.
     */
    public long currentLag() {
        long endOffset = broker.endOffset(topic);
        long committedOffset = broker.committedOffset(topic, consumerGroup);

        return Math.max(0, endOffset - committedOffset);
    }

    /**
     * Zapisuje aktualny consumer lag do observability.
     */
    private void recordLag() {
        observability.lag(topic, consumerGroup, currentLag());
    }
}