package pl.jakubtworek.marketplace.integration.kafka;

import tools.jackson.databind.ObjectMapper;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventMapper;
import pl.jakubtworek.marketplace.shared.events.ApplicationEventBus;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import pl.jakubtworek.marketplace.shared.observability.InMemoryFlowTraceRepository;
import pl.jakubtworek.marketplace.shared.observability.MarketplaceMetrics;
import pl.jakubtworek.marketplace.shared.observability.ObservabilityService;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Konsument wiadomości kafkowych używany w laboratorium niezawodnego messagingu.
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
    private final EventPublisher eventBus;

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
     * Obejmuje jedną lokalną transakcją efekt handlera, jego outbox oraz marker
     * processed_events. Commit offsetu celowo pozostaje poza tą granicą.
     */
    private final ConsumerUnitOfWork unitOfWork;

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
            EventPublisher eventBus,
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
                ),
                ConsumerUnitOfWork.direct()
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
            EventPublisher eventBus,
            ProcessedEventRepository processedEvents,
            DlqEventRepository dlqRepository,
            ObjectMapper objectMapper,
            RetryPolicy retryPolicy,
            ObservabilityService observability
    ) {
        this(consumerName, topic, consumerGroup, broker, eventBus, processedEvents,
                dlqRepository, objectMapper, retryPolicy, observability,
                ConsumerUnitOfWork.direct());
    }

    public KafkaConsumerWorker(
            String consumerName,
            String topic,
            String consumerGroup,
            KafkaMessageBroker broker,
            EventPublisher eventBus,
            ProcessedEventRepository processedEvents,
            DlqEventRepository dlqRepository,
            ObjectMapper objectMapper,
            RetryPolicy retryPolicy,
            ObservabilityService observability,
            ConsumerUnitOfWork unitOfWork
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
        this.unitOfWork = unitOfWork;
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
        unitOfWork.execute(() -> {
            if (processedEvents.exists(record.envelope().eventId(), consumerName)) {
                throw new IllegalStateException("event was already processed before crash simulation");
            }

            observability.eventReceived(record, consumerName);
            DomainEvent event = outboxEventMapper.toDomainEvent(
                    envelopeMapper.toOutboxEvent(record.envelope())
            );
            eventBus.publish(event);
            processedEvents.save(ProcessedEvent.processed(record.envelope().eventId(), consumerName));
            observability.eventProcessed(event, consumerName, record.topic(), 0);
            return null;
        });

        // Lokalna transakcja jest już zatwierdzona, ale Kafka nie otrzymała commitu.
        // Redelivery jest zatem oczekiwane i ma zostać zatrzymane przez marker.
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
        processRecord(record, () -> broker.commit(
                record.topic(), consumerGroup, record.partition(), record.offset()));
    }

    /**
     * Wariant dla kontenera {@code @KafkaListener}. Commit jest przekazany jako
     * callback, więc ten sam rdzeń semantyki może zatwierdzić offset przez
     * Acknowledgment należący do aktywnego konsumenta, zamiast otwierać drugi klient.
     */
    public void processRecord(KafkaRecord record, Runnable commitOffset) {
        UUID eventId = record.envelope().eventId();

        observability.eventReceived(record, consumerName);

        int attempts = 0;

        while (true) {
            attempts++;

            Instant startedAt = Instant.now();

            try {
                ProcessingResult result = unitOfWork.execute(() -> {
                    /*
                     * Sprawdzenie duplikatu musi znajdować się wewnątrz tej samej
                     * transakcji co efekt. Sam schemat "exists, efekt, insert" nadal
                     * wymaga unikalnego klucza w DB; przy równoległym duplikacie jeden
                     * zapis markera wygra, a drugi przebieg musi zostać wycofany.
                     */
                    if (processedEvents.exists(eventId, consumerName)) {
                        return ProcessingResult.duplicateResult();
                    }

                    DomainEvent event = outboxEventMapper.toDomainEvent(
                            envelopeMapper.toOutboxEvent(record.envelope())
                    );
                    eventBus.publish(event);
                    processedEvents.save(ProcessedEvent.processed(eventId, consumerName));
                    return ProcessingResult.processed(event);
                });

                /*
                 * Kafka nie uczestniczy w lokalnej transakcji. Commit następuje po
                 * jej sukcesie. Awaria w tym miejscu powoduje redelivery, ale nie
                 * ponowne wykonanie efektu, ponieważ marker został zatwierdzony.
                 */
                commitOffset.run();

                if (result.duplicate()) {
                    observability.duplicateSkipped(record, consumerName);
                } else {
                    observability.eventProcessed(
                            result.event(),
                            consumerName,
                            record.topic(),
                            Duration.between(startedAt, Instant.now()).toMillis()
                    );
                }

                recordLag();

                return;
            } catch (Exception e) {
                if (retryPolicy.shouldRetry(e, attempts)) {
                    /*
                     * Wyliczone opóźnienie powinno stać się timestampem wiadomości
                     * w retry topicu. Ten synchroniczny worker go nie przesypia,
                     * ponieważ blokowanie partycji podczas backoffu jest antywzorcem.
                     */
                    retryPolicy.delayFor(attempts, eventId);
                    observability.retryScheduled(record, consumerName, attempts, e);
                    continue;
                }

                /*
                 * Po przekroczeniu limitu prób zapisujemy event do DLQ.
                 *
                 * Następnie commitujemy offset, żeby ten sam wadliwy event nie blokował
                 * całej partycji w nieskończoność.
                 */
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

                    commitOffset.run();

                    recordLag();

                return;
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
        return broker.lag(topic, consumerGroup);
    }

    /**
     * Zapisuje aktualny consumer lag do observability.
     */
    private void recordLag() {
        observability.lag(topic, consumerGroup, currentLag());
    }

    private record ProcessingResult(DomainEvent event, boolean duplicate) {
        private static ProcessingResult processed(DomainEvent event) {
            return new ProcessingResult(event, false);
        }

        private static ProcessingResult duplicateResult() {
            return new ProcessingResult(null, true);
        }
    }
}
