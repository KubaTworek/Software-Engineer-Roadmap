package pl.jakubtworek.marketplace.shared.observability;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory implementacja repozytorium trace'ów przepływu.
 *
 * Flow trace pozwala śledzić, co działo się w ramach jednego procesu biznesowego,
 * np. dla konkretnego correlationId albo orderId.
 *
 * Przykładowy flow:
 * - event został odebrany z Kafki,
 * - event został przetworzony,
 * - nastąpił retry,
 * - event został wysłany do DLQ,
 * - zamówienie zostało potwierdzone albo odrzucone.
 *
 * Ta implementacja przechowuje wpisy wyłącznie w pamięci procesu.
 * Jest dobra do nauki, testów i lokalnej diagnostyki, ale nie jest trwała.
 * Po restarcie aplikacji wszystkie trace'y znikną.
 */
@Component
public class InMemoryFlowTraceRepository implements FlowTraceRepository {

    /**
     * Lista wpisów trace.
     *
     * CopyOnWriteArrayList upraszcza bezpieczny odczyt i zapis z wielu wątków.
     * To jest wygodne w testach i prostym observability, ale przy dużej liczbie wpisów
     * może być kosztowne, bo zapis tworzy kopię wewnętrznej tablicy.
     *
     * Produkcyjnie lepiej byłoby trzymać takie dane w systemie logów, tracingu
     * albo w bazie/indeksie diagnostycznym.
     */
    private final List<FlowTraceEntry> entries = new CopyOnWriteArrayList<>();

    /**
     * Dopisuje nowy wpis trace.
     *
     * Metoda jest używana przez ObservabilityService przy obsłudze eventów,
     * retry, DLQ i markerów biznesowych.
     */
    @Override
    public void append(FlowTraceEntry entry) {
        entries.add(entry);
    }

    /**
     * Wyszukuje wpisy trace po correlationId.
     *
     * correlationId identyfikuje cały przepływ biznesowy.
     * Dzięki temu można zobaczyć wszystkie kroki związane z jednym requestem
     * albo jednym flow event-driven.
     */
    @Override
    public List<FlowTraceEntry> findByCorrelationId(UUID correlationId) {
        return entries.stream()
                .filter(entry -> correlationId.equals(entry.correlationId()))
                .toList();
    }

    /**
     * Wyszukuje wpisy trace po orderId.
     *
     * To praktyczny endpoint diagnostyczny dla marketplace, bo operator często zna
     * identyfikator zamówienia, ale niekoniecznie zna correlationId.
     */
    @Override
    public List<FlowTraceEntry> findByOrderId(UUID orderId) {
        return entries.stream()
                .filter(entry -> orderId.equals(entry.orderId()))
                .toList();
    }

    /**
     * Zwraca kopię wszystkich wpisów trace.
     *
     * Nie zwracamy oryginalnej listy, żeby kod z zewnątrz nie mógł jej przypadkowo
     * zmodyfikować.
     */
    @Override
    public List<FlowTraceEntry> all() {
        return List.copyOf(entries);
    }

    /**
     * Czyści wszystkie wpisy trace.
     *
     * Metoda przydatna głównie w testach, żeby odizolować scenariusze testowe.
     * W produkcyjnej aplikacji raczej nie czyściłbym trace'ów w pamięci w ten sposób.
     */
    public void clear() {
        entries.clear();
    }
}