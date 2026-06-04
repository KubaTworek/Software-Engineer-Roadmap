package pl.jakubtworek.marketplace.shared.observability;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prosty, in-memory rejestr metryk aplikacyjnych.
 *
 * Ta klasa jest edukacyjnym odpowiednikiem prostego systemu metryk.
 * Przechowuje:
 * - counters, czyli liczniki narastające,
 * - gauges, czyli wartości chwilowe.
 *
 * Przykłady counters:
 * - events.received.total,
 * - events.processed.total,
 * - events.duplicates.skipped.total,
 * - consumer.retries.total,
 * - dlq.events.total.
 *
 * Przykłady gauges:
 * - consumer lag,
 * - ostatni czas przetwarzania eventu,
 * - liczba eventów oczekujących w DLQ.
 *
 * W produkcyjnej aplikacji podobną rolę zwykle pełni Micrometer + Prometheus,
 * ale na potrzeby tego projektu własny komponent dobrze pokazuje, jakie metryki
 * warto zbierać.
 */
@Component
public class MarketplaceMetrics {

    /**
     * Mapa liczników narastających.
     *
     * Kluczem jest nazwa metryki, a wartością AtomicLong z aktualną wartością licznika.
     *
     * ConcurrentHashMap i AtomicLong pozwalają bezpiecznie aktualizować metryki
     * z wielu wątków, np. z kilku konsumentów eventów.
     */
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    /**
     * Mapa wartości chwilowych.
     *
     * Gauge różni się od countera tym, że nie musi wyłącznie rosnąć.
     * Może rosnąć i maleć, np. consumer lag.
     */
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    /**
     * Zwiększa licznik o 1.
     *
     * Jeśli licznik o podanej nazwie jeszcze nie istnieje, zostanie utworzony
     * z wartością początkową 0, a następnie zwiększony.
     */
    public void increment(String name) {
        increment(name, 1);
    }

    /**
     * Zwiększa licznik o wskazaną wartość.
     *
     * delta pozwala zwiększać licznik o więcej niż 1, jeśli dana operacja reprezentuje
     * większą liczbę zdarzeń.
     */
    public void increment(String name, long delta) {
        counters.computeIfAbsent(
                name,
                ignored -> new AtomicLong()
        ).addAndGet(delta);
    }

    /**
     * Zwraca aktualną wartość licznika.
     *
     * Jeśli licznik nie istnieje, zwracamy 0.
     */
    public long counter(String name) {
        return counters.getOrDefault(
                name,
                new AtomicLong()
        ).get();
    }

    /**
     * Ustawia wartość gauge.
     *
     * Gauge jest wartością chwilową, więc nie zwiększamy go jak countera,
     * tylko ustawiamy konkretną wartość.
     *
     * Przykład:
     * consumer.lag.marketplace.order-events.v1.marketplace-ordering = 5
     */
    public void gauge(String name, long value) {
        gauges.computeIfAbsent(
                name,
                ignored -> new AtomicLong()
        ).set(value);
    }

    /**
     * Zwraca aktualną wartość gauge.
     *
     * Jeśli gauge nie istnieje, zwracamy 0.
     */
    public long gauge(String name) {
        return gauges.getOrDefault(
                name,
                new AtomicLong()
        ).get();
    }

    /**
     * Zwraca snapshot wszystkich counterów.
     *
     * Zwracamy Map<String, Long>, a nie Map<String, AtomicLong>, żeby nie wystawiać
     * wewnętrznych, mutowalnych struktur poza klasę.
     */
    public Map<String, Long> counters() {
        return counters.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().get()
                ));
    }

    /**
     * Zwraca snapshot wszystkich gauge'y.
     *
     * Podobnie jak przy counters(), zwracamy zwykłe wartości Long,
     * a nie referencje do AtomicLong.
     */
    public Map<String, Long> gauges() {
        return gauges.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().get()
                ));
    }

    /**
     * Czyści wszystkie metryki.
     *
     * Metoda przydatna głównie w testach, żeby odizolować scenariusze testowe.
     * W działającej aplikacji produkcyjnej raczej nie powinno się resetować metryk
     * w ten sposób.
     */
    public void clear() {
        counters.clear();
        gauges.clear();
    }
}