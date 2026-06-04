package pl.jakubtworek.marketplace.shared.observability;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

/**
 * Zakres MDC, który automatycznie przywraca poprzednie wartości po zakończeniu pracy.
 *
 * MDC, czyli Mapped Diagnostic Context, jest mechanizmem SLF4J pozwalającym dopisywać
 * do logów dodatkowe pola, np. correlationId, eventId, orderId, consumerName albo topic.
 *
 * MdcScope jest przeznaczony do użycia z try-with-resources:
 *
 * try (var ignored = MdcScope.open()
 *         .put("correlationId", correlationId)
 *         .put("eventId", eventId)) {
 *     // logi w tym bloku mają ustawione correlationId i eventId
 * }
 *
 * Po wyjściu z bloku close() automatycznie przywróci poprzedni stan MDC.
 * To jest ważne, bo wątki w aplikacji webowej i konsumentach eventów są często używane
 * wielokrotnie. Bez czyszczenia MDC kolejny request albo event mógłby odziedziczyć
 * dane diagnostyczne z poprzedniego przetwarzania.
 */
public final class MdcScope implements AutoCloseable {

    /**
     * Poprzednie wartości kluczy MDC zmienionych w ramach tego scope'a.
     *
     * Dla każdego klucza zapamiętujemy wartość, która była ustawiona przed pierwszym
     * wywołaniem put(...) dla tego klucza.
     *
     * Jeśli poprzednia wartość była null, close() usunie ten klucz z MDC.
     * Jeśli poprzednia wartość istniała, close() ją przywróci.
     */
    private final Map<String, String> previousValues = new HashMap<>();

    /**
     * Flaga zabezpieczająca przed ponownym użyciem zamkniętego scope'a.
     *
     * close() jest idempotentne, czyli można je wywołać więcej niż raz bez błędu.
     * Natomiast put(...) po close() jest błędem programistycznym.
     */
    private boolean closed;

    /**
     * Prywatny konstruktor wymusza tworzenie scope'a przez metodę open().
     */
    private MdcScope() {
    }

    /**
     * Otwiera nowy zakres MDC.
     *
     * Zwrócony obiekt powinien zostać zamknięty przez try-with-resources albo ręcznie
     * przez close().
     */
    public static MdcScope open() {
        return new MdcScope();
    }

    /**
     * Ustawia wartość w MDC i zapamiętuje poprzednią wartość tego klucza.
     *
     * Jeśli scope jest już zamknięty, metoda rzuca wyjątek.
     *
     * previousValues.putIfAbsent(...) jest tutaj celowe:
     * - jeśli ten sam klucz ustawimy kilka razy w jednym scope,
     * - zapamiętana zostanie tylko wartość sprzed pierwszej zmiany,
     * - close() przywróci stan sprzed otwarcia scope'a, a nie stan pośredni.
     *
     * Jeśli value jest null, klucz zostanie usunięty z MDC.
     * Jeśli value nie jest null, zostanie zapisany jako String.
     */
    public MdcScope put(String key, Object value) {
        if (closed) {
            throw new IllegalStateException("MDC scope is already closed");
        }

        previousValues.putIfAbsent(key, MDC.get(key));

        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value.toString());
        }

        return this;
    }

    /**
     * Zamyka scope i przywraca poprzedni stan MDC.
     *
     * Działanie:
     * - jeśli klucz wcześniej nie istniał, zostaje usunięty,
     * - jeśli klucz miał poprzednią wartość, zostaje ona przywrócona.
     *
     * Metoda jest idempotentna. Drugie i kolejne wywołanie close() nic nie zrobi.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }

        previousValues.forEach((key, previous) -> {
            if (previous == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, previous);
            }
        });

        closed = true;
    }
}