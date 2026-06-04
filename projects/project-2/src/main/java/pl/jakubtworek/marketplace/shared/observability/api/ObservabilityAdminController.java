package pl.jakubtworek.marketplace.shared.observability.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.marketplace.integration.kafka.DlqEventRepository;
import pl.jakubtworek.marketplace.integration.kafka.DlqEventStatus;
import pl.jakubtworek.marketplace.shared.observability.FlowTraceRepository;
import pl.jakubtworek.marketplace.shared.observability.MarketplaceMetrics;

import java.util.Map;
import java.util.UUID;

/**
 * Administracyjny kontroler observability.
 *
 * Ten kontroler nie jest częścią publicznego API biznesowego.
 * Służy do diagnostyki działania systemu event-driven:
 * - podglądu trace'ów po correlationId,
 * - podglądu trace'ów po orderId,
 * - sprawdzania prostych metryk aplikacyjnych,
 * - analizy powodów trafienia eventów do DLQ.
 *
 * W prawdziwym systemie endpointy administracyjne powinny być zabezpieczone:
 * - autoryzacją,
 * - audytem,
 * - ograniczeniem dostępu tylko dla operatorów/administratorów,
 * - ewentualnym maskowaniem wrażliwych danych z payloadów.
 */
@RestController
@RequestMapping("/admin/observability")
public class ObservabilityAdminController {

    /**
     * Repozytorium trace'ów przepływu eventów.
     *
     * Pozwala odtworzyć, co działo się z konkretnym flow biznesowym,
     * np. od OrderPlaced przez PaymentReserved i StockReserved aż do OrderConfirmed.
     */
    private final FlowTraceRepository traces;

    /**
     * Prosty komponent metryk aplikacyjnych.
     *
     * Przechowuje liczniki i gauge'e używane do diagnostyki:
     * - liczby przetworzonych eventów,
     * - liczby retry,
     * - liczby eventów wysłanych do DLQ,
     * - laga konsumentów.
     */
    private final MarketplaceMetrics metrics;

    /**
     * Repozytorium DLQ.
     *
     * Używane tutaj do pokazania liczby nowych eventów w DLQ oraz powodów błędów.
     */
    private final DlqEventRepository dlqRepository;

    public ObservabilityAdminController(
            FlowTraceRepository traces,
            MarketplaceMetrics metrics,
            DlqEventRepository dlqRepository
    ) {
        this.traces = traces;
        this.metrics = metrics;
        this.dlqRepository = dlqRepository;
    }

    /**
     * Zwraca trace'y powiązane z konkretnym correlationId.
     *
     * correlationId służy do śledzenia jednego przepływu biznesowego przez wiele modułów.
     *
     * Przykład:
     * - klient składa zamówienie,
     * - powstaje OrderPlaced,
     * - Payment rezerwuje płatność,
     * - Inventory rezerwuje stock,
     * - Ordering potwierdza albo odrzuca zamówienie.
     *
     * Wszystkie te kroki powinny mieć ten sam correlationId.
     */
    @GetMapping("/trace/correlation/{correlationId}")
    public ResponseEntity<?> traceByCorrelationId(@PathVariable UUID correlationId) {
        return ResponseEntity.ok(
                traces.findByCorrelationId(correlationId)
        );
    }

    /**
     * Zwraca trace'y powiązane z konkretnym orderId.
     *
     * Ten endpoint jest wygodny operacyjnie, bo w praktyce często diagnozujemy problem
     * zaczynając od identyfikatora zamówienia, a nie od correlationId.
     *
     * Pozwala odpowiedzieć na pytania:
     * - czy OrderPlaced zostało opublikowane,
     * - czy PaymentReserved albo PaymentRejected zostało obsłużone,
     * - czy StockReserved albo StockReservationFailed zostało obsłużone,
     * - dlaczego zamówienie nie przeszło do oczekiwanego statusu.
     */
    @GetMapping("/trace/order/{orderId}")
    public ResponseEntity<?> traceByOrderId(@PathVariable UUID orderId) {
        return ResponseEntity.ok(
                traces.findByOrderId(orderId)
        );
    }

    /**
     * Zwraca podstawowe metryki diagnostyczne aplikacji.
     *
     * Odpowiedź zawiera:
     * - counters — liczniki zdarzeń, np. liczba przetworzonych eventów,
     * - gauges — wartości chwilowe, np. consumer lag,
     * - dlqNewCount — liczba nowych eventów w DLQ.
     *
     * To jest uproszczony endpoint metryk na potrzeby projektu edukacyjnego.
     * W produkcyjnej aplikacji większość takich danych powinna być eksportowana
     * przez Micrometer/Prometheus, a nie tylko przez własny endpoint adminowy.
     */
    @GetMapping("/metrics")
    public ResponseEntity<?> metrics() {
        return ResponseEntity.ok(Map.of(
                "counters", metrics.counters(),
                "gauges", metrics.gauges(),
                "dlqNewCount", dlqRepository.findByStatus(DlqEventStatus.NEW, 1000).size()
        ));
    }

    /**
     * Zwraca uproszczoną listę powodów trafienia eventów do DLQ.
     *
     * Endpoint nie zwraca pełnych payloadów eventów, tylko najważniejsze informacje
     * diagnostyczne:
     * - techniczne ID wpisu DLQ,
     * - eventId oryginalnego eventu,
     * - eventType,
     * - correlationId,
     * - reason,
     * - attempts,
     * - status.
     *
     * Dzięki temu można szybko sprawdzić, które eventy się wysypują i dlaczego,
     * bez przeglądania pełnych rekordów DLQ.
     */
    @GetMapping("/dlq/reasons")
    public ResponseEntity<?> dlqReasons() {
        return ResponseEntity.ok(
                dlqRepository.findAll().stream()
                        .map(event -> Map.of(
                                "id", event.id(),
                                "eventId", event.envelope().eventId(),
                                "eventType", event.envelope().eventType(),
                                "correlationId", event.envelope().correlationId(),
                                "reason", event.reason(),
                                "attempts", event.attempts(),
                                "status", event.status()
                        ))
                        .toList()
        );
    }
}