package pl.jakubtworek.marketplace.integration.outbox;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.contracts.OrderPlacedContractMapper;
import pl.jakubtworek.marketplace.inventory.domain.StockReservationFailed;
import pl.jakubtworek.marketplace.inventory.domain.StockReserved;
import pl.jakubtworek.marketplace.ordering.domain.OrderCancelled;
import pl.jakubtworek.marketplace.ordering.domain.OrderConfirmed;
import pl.jakubtworek.marketplace.ordering.domain.OrderPlaced;
import pl.jakubtworek.marketplace.payment.domain.PaymentRejected;
import pl.jakubtworek.marketplace.payment.domain.PaymentReserved;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.time.Instant;
import java.util.Map;

/**
 * Mapper odpowiedzialny za konwersję między DomainEvent a OutboxEvent.
 *
 * DomainEvent to zdarzenie domenowe używane w kodzie aplikacji, np. OrderPlaced,
 * PaymentReserved albo StockReserved.
 *
 * OutboxEvent to techniczna reprezentacja zdarzenia zapisywana w outboxie.
 * Zawiera metadane potrzebne do niezawodnej publikacji:
 * - eventId,
 * - aggregateId,
 * - aggregateType,
 * - eventType,
 * - eventVersion,
 * - payload,
 * - correlationId,
 * - causationId,
 * - status publikacji,
 * - retryCount,
 * - lastError.
 *
 * Ta klasa jest elementem warstwy integration/outbox, więc zna szczegóły serializacji.
 * Domena nie powinna wiedzieć, że jej eventy są serializowane do JSON-a i zapisywane
 * w tabeli outbox_events.
 */
@Component
public class OutboxEventMapper {

    /**
     * ObjectMapper używany do serializacji i deserializacji eventów.
     *
     * Używamy kopii mappera z kontekstu Springa, żeby lokalna konfiguracja tej klasy
     * nie zmieniała globalnego ObjectMappera aplikacji.
     *
     * findAndRegisterModules() pozwala obsługiwać typy takie jak Instant, UUID,
     * BigDecimal czy typy z modułów Javy.
     *
     * FAIL_ON_UNKNOWN_PROPERTIES = false jest ważne dla kompatybilności kontraktów:
     * jeśli w przyszłości event dostanie nowe pole, starszy konsument nie powinien
     * automatycznie przestać działać.
     */
    private final ObjectMapper objectMapper;

    /**
     * Mapper kontraktu dla OrderPlaced.
     *
     * OrderPlaced ma specjalną obsługę, ponieważ w fazie versioningu obsługujemy
     * więcej niż jedną wersję tego eventu, np. OrderPlacedV1 i OrderPlacedV2.
     *
     * Zamiast deserializować OrderPlaced tak samo jak pozostałe eventy, delegujemy
     * konwersję do OrderPlacedContractMapper.
     */
    private final OrderPlacedContractMapper orderPlacedContractMapper;

    /**
     * Mapa wspieranych typów eventów.
     *
     * Kluczem jest logiczna nazwa eventu zapisywana w outboxie, np. "PaymentReserved".
     * Wartością jest klasa Javy, do której można zdeserializować payload JSON.
     *
     * Jeśli event nie znajduje się w tej mapie i nie ma specjalnej obsługi,
     * mapper odrzuci go jako nieobsługiwany.
     */
    private final Map<String, Class<? extends DomainEvent>> eventTypes = Map.of(
            "OrderPlaced", OrderPlaced.class,
            "OrderCancelled", OrderCancelled.class,
            "OrderConfirmed", OrderConfirmed.class,
            "PaymentReserved", PaymentReserved.class,
            "PaymentRejected", PaymentRejected.class,
            "StockReserved", StockReserved.class,
            "StockReservationFailed", StockReservationFailed.class
    );

    public OutboxEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.orderPlacedContractMapper = new OrderPlacedContractMapper(this.objectMapper);
    }

    /**
     * Zamienia DomainEvent na OutboxEvent.
     *
     * Ta metoda jest używana przez OutboxEventPublisher.
     *
     * Przepływ:
     * 1. Event domenowy jest serializowany do JSON-a.
     * 2. Z eventu pobierane są metadane: eventId, aggregateId, eventType itd.
     * 3. Tworzony jest OutboxEvent ze statusem NEW.
     * 4. OutboxEvent może zostać zapisany w repozytorium outboxa.
     *
     * Status NEW oznacza, że event został zapisany, ale nie został jeszcze opublikowany
     * przez workera.
     */
    public OutboxEvent toOutboxEvent(DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            return new OutboxEvent(
                    event.eventId(),
                    event.aggregateId(),
                    aggregateType(event),
                    event.eventType(),
                    event.eventVersion(),
                    payload,
                    event.correlationId(),
                    event.causationId(),
                    OutboxEventStatus.NEW,
                    Instant.now(),
                    null,
                    0,
                    null
            );
        } catch (Exception e) {
            throw new OutboxSerializationException(
                    "Cannot serialize event " + event.eventType(),
                    e
            );
        }
    }

    /**
     * Zamienia OutboxEvent z powrotem na DomainEvent.
     *
     * Ta metoda jest używana przez workery publikujące eventy z outboxa.
     *
     * Dla większości eventów wystarczy zwykła deserializacja JSON-a do klasy Javy.
     * Wyjątkiem jest OrderPlaced, ponieważ ten event ma jawne wersjonowanie kontraktu.
     *
     * Jeśli eventType jest nieznany, rzucamy wyjątek. Dzięki temu worker może oznaczyć
     * event jako FAILED albo wysłać go do DLQ w późniejszym etapie.
     */
    public DomainEvent toDomainEvent(OutboxEvent outboxEvent) {
        if ("OrderPlaced".equals(outboxEvent.eventType())) {
            return orderPlacedContractMapper.toDomainEvent(
                    outboxEvent.payload(),
                    outboxEvent.eventVersion()
            );
        }

        Class<? extends DomainEvent> targetType = eventTypes.get(outboxEvent.eventType());

        if (targetType == null) {
            throw new IllegalArgumentException(
                    "Unsupported outbox event type: " + outboxEvent.eventType()
            );
        }

        try {
            return objectMapper.readValue(
                    outboxEvent.payload(),
                    targetType
            );
        } catch (Exception e) {
            throw new OutboxSerializationException(
                    "Cannot deserialize event " + outboxEvent.eventType(),
                    e
            );
        }
    }

    /**
     * Określa typ agregatu na podstawie klasy eventu.
     *
     * aggregateType jest metadanym technicznym używanym w outboxie do filtrowania,
     * diagnostyki i potencjalnego routingu eventów.
     *
     * Przykłady:
     * - OrderPlaced -> Order,
     * - PaymentReserved -> Payment,
     * - StockReserved -> StockReservation.
     *
     * Obecna implementacja bazuje na nazwie klasy eventu. Jest prosta, ale dość krucha:
     * zmiana nazwy klasy może zmienić aggregateType.
     */
    private String aggregateType(DomainEvent event) {
        String className = event.getClass().getSimpleName();

        if (className.startsWith("Order")) {
            return "Order";
        }

        if (className.startsWith("Payment")) {
            return "Payment";
        }

        if (className.startsWith("Stock")) {
            return "StockReservation";
        }

        return "Unknown";
    }
}