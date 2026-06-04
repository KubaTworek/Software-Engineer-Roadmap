package pl.jakubtworek.marketplace.shared.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.kafka.DlqEvent;
import pl.jakubtworek.marketplace.integration.kafka.KafkaRecord;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.util.UUID;

/**
 * Serwis observability dla przepływów event-driven.
 *
 * Ta klasa centralizuje techniczną diagnostykę systemu:
 * - zapis trace'ów przepływu eventów,
 * - logowanie z correlationId/eventId/orderId w MDC,
 * - inkrementowanie liczników metryk,
 * - zapisywanie gauge'y, np. czasu przetwarzania albo consumer laga,
 * - rejestrowanie retry, duplikatów i DLQ.
 *
 * Dzięki temu logika konsumentów Kafki nie musi bezpośrednio znać szczegółów
 * logowania, metryk ani repozytorium trace'ów.
 */
@Component
public class ObservabilityService {

    /**
     * Logger używany do strukturalnego logowania zdarzeń observability.
     *
     * Dzięki CorrelationContext logi mogą automatycznie zawierać pola z MDC,
     * np. correlationId, causationId, eventId, orderId, consumerName i topic.
     */
    private static final Logger log = LoggerFactory.getLogger(ObservabilityService.class);

    /**
     * Repozytorium trace'ów przepływu.
     *
     * Pozwala później odtworzyć historię jednego flow, np. po correlationId albo orderId.
     */
    private final FlowTraceRepository flowTraceRepository;

    /**
     * Prosty komponent metryk.
     *
     * Przechowuje liczniki i gauge'e używane przez endpointy administracyjne
     * oraz health/observability.
     */
    private final MarketplaceMetrics metrics;

    public ObservabilityService(
            FlowTraceRepository flowTraceRepository,
            MarketplaceMetrics metrics
    ) {
        this.flowTraceRepository = flowTraceRepository;
        this.metrics = metrics;
    }

    /**
     * Rejestruje odebranie eventu przez konsumenta.
     *
     * Wywoływane na początku przetwarzania rekordu z Kafki.
     *
     * Efekty:
     * - ustawia dane eventu w MDC na czas logowania,
     * - zapisuje log "event received",
     * - dopisuje wpis trace o statusie RECEIVED,
     * - zwiększa licznik events.received.total.
     */
    public void eventReceived(KafkaRecord record, String consumerName) {
        var envelope = record.envelope();

        try (var ignored = CorrelationContext.withEvent(
                envelope.correlationId(),
                envelope.causationId(),
                envelope.eventId(),
                envelope.aggregateId(),
                consumerName,
                record.topic()
        )) {
            log.info(
                    "event received from topic={} offset={} type={} version={} consumer={}",
                    record.topic(),
                    record.offset(),
                    envelope.eventType(),
                    envelope.eventVersion(),
                    consumerName
            );
        }

        flowTraceRepository.append(FlowTraceEntry.of(
                envelope.correlationId(),
                envelope.eventId(),
                envelope.aggregateId(),
                "kafka-consumer",
                "RECEIVED",
                record.topic(),
                consumerName,
                envelope.eventType()
        ));

        metrics.increment("events.received.total");
    }

    /**
     * Rejestruje skuteczne przetworzenie eventu.
     *
     * Wywoływane po tym, jak konsument:
     * - odtworzył DomainEvent,
     * - uruchomił handlery,
     * - zapisał processed_events,
     * - wykonał commit offsetu.
     *
     * Efekty:
     * - zapisuje log "event processed",
     * - dopisuje trace PROCESSED,
     * - zwiększa licznik events.processed.total,
     * - zapisuje ostatni czas przetwarzania dla danego konsumenta.
     */
    public void eventProcessed(
            DomainEvent event,
            String consumerName,
            String topic,
            long durationMillis
    ) {
        try (var ignored = CorrelationContext.withEvent(
                event.correlationId(),
                event.causationId(),
                event.eventId(),
                event.aggregateId(),
                consumerName,
                topic
        )) {
            log.info(
                    "event processed type={} version={} durationMs={} consumer={}",
                    event.eventType(),
                    event.eventVersion(),
                    durationMillis,
                    consumerName
            );
        }

        flowTraceRepository.append(FlowTraceEntry.of(
                event.correlationId(),
                event.eventId(),
                event.aggregateId(),
                "kafka-consumer",
                "PROCESSED",
                topic,
                consumerName,
                event.eventType()
        ));

        metrics.increment("events.processed.total");
        metrics.gauge("event.processing.duration.last.ms." + consumerName, durationMillis);
    }

    /**
     * Rejestruje pominięcie duplikatu eventu.
     *
     * Wywoływane, gdy konsument wykryje, że dany eventId został już przetworzony
     * przez tego samego konsumenta.
     *
     * To jest normalna sytuacja w modelu at-least-once delivery.
     * Event może zostać dostarczony ponownie, np. gdy aplikacja padła po zapisie
     * processed_events, ale przed commitem offsetu.
     */
    public void duplicateSkipped(KafkaRecord record, String consumerName) {
        var envelope = record.envelope();

        try (var ignored = CorrelationContext.withEvent(
                envelope.correlationId(),
                envelope.causationId(),
                envelope.eventId(),
                envelope.aggregateId(),
                consumerName,
                record.topic()
        )) {
            log.info(
                    "duplicate event skipped type={} consumer={}",
                    envelope.eventType(),
                    consumerName
            );
        }

        flowTraceRepository.append(FlowTraceEntry.of(
                envelope.correlationId(),
                envelope.eventId(),
                envelope.aggregateId(),
                "kafka-consumer",
                "DUPLICATE_SKIPPED",
                record.topic(),
                consumerName,
                envelope.eventType()
        ));

        metrics.increment("events.duplicates.skipped.total");
    }

    /**
     * Rejestruje kolejną próbę przetworzenia eventu po błędzie.
     *
     * Wywoływane w catch podczas pracy konsumenta.
     *
     * Efekty:
     * - zapisuje ostrzeżenie w logach,
     * - dopisuje trace RETRY,
     * - zwiększa licznik retry dla konkretnego konsumenta,
     * - zwiększa globalny licznik retry.
     */
    public void retryScheduled(
            KafkaRecord record,
            String consumerName,
            int attempt,
            Exception exception
    ) {
        var envelope = record.envelope();

        try (var ignored = CorrelationContext.withEvent(
                envelope.correlationId(),
                envelope.causationId(),
                envelope.eventId(),
                envelope.aggregateId(),
                consumerName,
                record.topic()
        )) {
            log.warn(
                    "event processing retry attempt={} type={} reason={}",
                    attempt,
                    envelope.eventType(),
                    exception.getMessage()
            );
        }

        flowTraceRepository.append(FlowTraceEntry.of(
                envelope.correlationId(),
                envelope.eventId(),
                envelope.aggregateId(),
                "kafka-consumer",
                "RETRY",
                record.topic(),
                consumerName,
                exception.getMessage()
        ));

        metrics.increment("consumer.retries.total." + consumerName);
        metrics.increment("consumer.retries.total");
    }

    /**
     * Rejestruje wysłanie eventu do DLQ.
     *
     * Wywoływane po przekroczeniu maksymalnej liczby prób retry.
     *
     * Efekty:
     * - zapisuje błąd w logach,
     * - dopisuje trace SENT_TO_DLQ,
     * - zwiększa licznik dlq.events.total.
     */
    public void sentToDlq(DlqEvent event) {
        var envelope = event.envelope();

        try (var ignored = CorrelationContext.withEvent(
                envelope.correlationId(),
                envelope.causationId(),
                envelope.eventId(),
                envelope.aggregateId(),
                event.consumerGroup(),
                event.topic()
        )) {
            log.error(
                    "event sent to DLQ type={} reason={} attempts={}",
                    envelope.eventType(),
                    event.reason(),
                    event.attempts()
            );
        }

        flowTraceRepository.append(FlowTraceEntry.of(
                envelope.correlationId(),
                envelope.eventId(),
                envelope.aggregateId(),
                "dlq",
                "SENT_TO_DLQ",
                event.topic(),
                event.consumerGroup(),
                event.reason()
        ));

        metrics.increment("dlq.events.total");
    }

    /**
     * Aktualizuje gauge z aktualnym consumer lagiem.
     *
     * Lag mówi, ile wiadomości konsument ma jeszcze do przetworzenia.
     *
     * Nazwa metryki zawiera topic i consumerGroup, żeby można było diagnozować
     * opóźnienia osobno dla różnych konsumentów.
     */
    public void lag(String topic, String consumerGroup, long lag) {
        metrics.gauge(
                "consumer.lag." + topic + "." + consumerGroup,
                lag
        );
    }

    /**
     * Dopisuje trace dla zdarzenia biznesowego.
     *
     * Przydatne poza samymi konsumentami Kafki, np. gdy use case albo handler chce
     * oznaczyć ważny krok biznesowy w flow.
     */
    public void businessEvent(
            DomainEvent event,
            String component,
            String action
    ) {
        flowTraceRepository.append(FlowTraceEntry.of(
                event.correlationId(),
                event.eventId(),
                event.aggregateId(),
                component,
                action,
                null,
                null,
                event.eventType()
        ));
    }

    /**
     * Dopisuje ręczny marker biznesowy do trace.
     *
     * Użyteczne, gdy chcemy zapisać informację diagnostyczną, która nie jest bezpośrednio
     * związana z konkretnym DomainEvent, ale nadal dotyczy danego flow.
     *
     * Przykład:
     * - "order rejected because stock was missing",
     * - "manual replay started",
     * - "payment gateway returned temporary error".
     */
    public void businessMarker(
            UUID correlationId,
            UUID orderId,
            String component,
            String action,
            String message
    ) {
        flowTraceRepository.append(FlowTraceEntry.of(
                correlationId,
                null,
                orderId,
                component,
                action,
                null,
                null,
                message
        ));
    }
}