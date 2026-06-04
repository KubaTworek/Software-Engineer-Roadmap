package pl.jakubtworek.marketplace.shared.observability;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

/**
 * Pomocnicza klasa do obsługi kontekstu korelacji w logach.
 *
 * CorrelationContext korzysta z MDC, czyli Mapped Diagnostic Context z SLF4J.
 * MDC pozwala dopisać do każdego loga dodatkowe pola techniczne, np.:
 * - correlationId,
 * - causationId,
 * - eventId,
 * - orderId,
 * - consumerName,
 * - topic.
 *
 * Dzięki temu można później prześledzić cały przepływ jednego zamówienia przez:
 * - HTTP API,
 * - outbox,
 * - Kafkę,
 * - konsumentów,
 * - DLQ,
 * - retry.
 *
 * Klasa jest finalna i ma prywatny konstruktor, bo pełni rolę statycznego helpera.
 */
public final class CorrelationContext {

    /**
     * Identyfikator korelacji całego przepływu biznesowego.
     *
     * Ten sam correlationId powinien przechodzić przez cały flow, np.:
     * OrderPlaced -> PaymentReserved -> StockReserved -> OrderConfirmed.
     */
    public static final String CORRELATION_ID = "correlationId";

    /**
     * Identyfikator zdarzenia, które spowodowało aktualne zdarzenie albo akcję.
     *
     * Przykład:
     * - PaymentReserved może mieć causationId ustawione na eventId zdarzenia OrderPlaced.
     */
    public static final String CAUSATION_ID = "causationId";

    /**
     * Identyfikator aktualnie obsługiwanego eventu.
     *
     * Przydatny do śledzenia konkretnej wiadomości w outboxie, Kafce, retry i DLQ.
     */
    public static final String EVENT_ID = "eventId";

    /**
     * Identyfikator zamówienia.
     *
     * Nie każdy event musi dotyczyć zamówienia, ale w tym projekcie większość przepływów
     * event-driven jest powiązana właśnie z orderId.
     */
    public static final String ORDER_ID = "orderId";

    /**
     * Nazwa konsumenta przetwarzającego event.
     *
     * Przydatne przy diagnostyce, gdy wiele consumerów przetwarza różne topiki
     * albo różne typy eventów.
     */
    public static final String CONSUMER_NAME = "consumerName";

    /**
     * Nazwa topicu, z którego pochodzi event albo do którego został opublikowany.
     */
    public static final String TOPIC = "topic";

    /**
     * Prywatny konstruktor blokuje tworzenie instancji tej klasy.
     */
    private CorrelationContext() {
    }

    /**
     * Pobiera aktualny correlationId z MDC albo generuje nowy.
     *
     * Jeśli w MDC istnieje poprawna wartość correlationId, metoda ją zwraca.
     * Jeśli correlationId nie istnieje albo jest pusty, generowany jest nowy UUID.
     *
     * Ta metoda jest przydatna np. w kontrolerach HTTP:
     * - jeśli klient przekaże correlationId, możemy go użyć,
     * - jeśli go nie przekaże, system utworzy nowy.
     *
     * Uwaga:
     * UUID::fromString rzuci wyjątek, jeśli w MDC znajduje się niepoprawny tekst.
     * Jeśli correlationId może pochodzić z niepewnego źródła, warto obsłużyć ten przypadek.
     */
    public static UUID currentOrNewCorrelationId() {
        return Optional.ofNullable(MDC.get(CORRELATION_ID))
                .filter(value -> !value.isBlank())
                .map(UUID::fromString)
                .orElseGet(UUID::randomUUID);
    }

    /**
     * Dodaje wartość do MDC.
     *
     * Jeśli value jest null, metoda nic nie robi.
     * Dzięki temu nie zapisujemy do MDC pustych albo przypadkowych wartości "null".
     */
    public static void put(String key, Object value) {
        if (value != null) {
            MDC.put(key, value.toString());
        }
    }

    /**
     * Usuwa wskazane klucze z MDC.
     *
     * To ważne szczególnie w aplikacjach webowych i konsumentach eventów, ponieważ wątki
     * są często używane ponownie przez pulę wątków.
     *
     * Jeśli nie wyczyścimy MDC po zakończeniu obsługi requestu albo eventu, kolejny request
     * obsługiwany przez ten sam wątek może odziedziczyć stare correlationId lub eventId.
     */
    public static void remove(String... keys) {
        for (String key : keys) {
            MDC.remove(key);
        }
    }

    /**
     * Otwiera zakres MDC dla obsługi eventu.
     *
     * Metoda ustawia w MDC najważniejsze informacje diagnostyczne:
     * - correlationId,
     * - causationId,
     * - eventId,
     * - orderId,
     * - consumerName,
     * - topic.
     *
     * Zwracany MdcScope powinien być używany w try-with-resources, żeby automatycznie
     * wyczyścić MDC po zakończeniu obsługi eventu.
     *
     * Przykład:
     *
     * try (var ignored = CorrelationContext.withEvent(
     *         correlationId,
     *         causationId,
     *         eventId,
     *         orderId,
     *         consumerName,
     *         topic
     * )) {
     *     // logi w tym bloku będą zawierały dane z MDC
     * }
     */
    public static MdcScope withEvent(
            UUID correlationId,
            UUID causationId,
            UUID eventId,
            UUID orderId,
            String consumerName,
            String topic
    ) {
        return MdcScope.open()
                .put(CORRELATION_ID, correlationId)
                .put(CAUSATION_ID, causationId)
                .put(EVENT_ID, eventId)
                .put(ORDER_ID, orderId)
                .put(CONSUMER_NAME, consumerName)
                .put(TOPIC, topic);
    }
}