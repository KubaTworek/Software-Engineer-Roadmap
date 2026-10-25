# Wartości, wyniki i zakresy zasobów w Javie

[Mapa przykładów](../LABORATORIA.md) · [Karty wzorców](../catalog/04-java/README.md)

Ten dokument zawiera rozwinięty model i scenariusze jego poprawności w blokach Markdown. Karty katalogu
pozostają punktem wejścia do intuicji, konkretnych zastosowań, kosztu i kontrprzykładów. Modele mają zakres
lokalny i in-memory; trwałość oraz granice rozproszone wymagają osobnego kontraktu.

## Model

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: JavaPatterns</summary>

```java
package pl.jakubtworek.patterns.java;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Idiomy Javy i wzorce aplikacyjne bez kontenera DI.
 *
 * <p>Ten plik pokazuje mały mechanizm; większy przepływ agregatu i repozytorium
 * rozwija labs.domain.OrderWorkflow, a zakres wypożyczenia labs.concurrency.ResourcePool.
 */
public final class JavaPatterns {
    private JavaPatterns() {
    }

    // VALUE OBJECT + IMMUTABILITY: równość z wartości; nie wolno dodawać różnych walut.
    public record Money(long cents, String currency) {
        public Money {
            Objects.requireNonNull(currency);
        }

        public Money plus(Money other) {
            // Jednostka jest częścią wartości. Sama zgodność typów long nie wystarcza do dodawania.
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("currency mismatch");
            }
            return new Money(Math.addExact(cents, other.cents), currency);
        }
    }

    // RESULT TYPE: oczekiwany błąd jest częścią kontraktu, bez wartości null.
    public sealed interface Result<T> permits Success, Failure {
    }

    public record Success<T>(T value) implements Result<T> {
    }

    public record Failure<T>(String reason) implements Result<T> {
    }

    public static Result<Integer> positiveInteger(String input) {
        try {
            int value = Integer.parseInt(input);
            return value > 0 ? new Success<>(value) : new Failure<>("not positive");
        } catch (NumberFormatException e) {
            // To oczekiwane niepoprawne dane użytkownika, nie sygnał awarii infrastruktury.
            return new Failure<>("not an integer");
        }
    }

    // SPECIFICATION: kompozycja reguł domenowych przez and/or/negate z interfejsu Predicate.
    public record Customer(boolean active, int unpaidInvoices) {
    }

    public static final Predicate<Customer> CAN_ORDER =
            ((Predicate<Customer>) Customer::active).and(customer -> customer.unpaidInvoices() == 0);

    // DEPENDENCY INJECTION + PORT/ADAPTER: aplikacja zależy od portu, adapter podaje composition root.
    public interface OrderRepository {
        void save(String order);
    }

    public record OrderService(OrderRepository repository) {
        public void place(String order) {
            if (order.isBlank()) {
                throw new IllegalArgumentException("blank order");
            }
            repository.save(order);
        }
    }

    // NULL OBJECT: jawna polityka braku audytu; w krytycznym audycie taki wybór byłby błędem.
    public interface Audit {
        void record(String event);
    }

    public static final Audit NO_AUDIT = event -> {
    };

    // EXECUTE AROUND: właściciel zakresu gwarantuje zwolnienie zasobu także po wyjątku.
    public interface Session extends AutoCloseable {
        void write(String value);

        @Override
        void close();
    }

    public static void writeAndClose(Session session, List<String> values) {
        // Własność zasobu przechodzi na tę funkcję. Po powrocie sesja nie nadaje się do użycia.
        try (session) {
            values.forEach(session::write);
        }
    }

    // FUNCTIONAL CORE / IMPERATIVE SHELL: wynik jest danymi, shell wykonuje skutki uboczne.
    public record Decision(boolean accepted, List<String> events) {
        public Decision {
            events = List.copyOf(events);
        }
    }

    public static Decision decide(boolean available) {
        // Zwracamy zamiar, nie wykonujemy efektu. Dzięki temu decyzję można powtarzać i porównywać.
        return new Decision(available, available ? List.of("OrderAccepted") : List.of("OrderRejected"));
    }
}
```

</details>

## Scenariusze poprawności

Scenariusze poprawności w notacji JUnit. Pokazują dane, działanie i oczekiwany stan. Są materiałem
dokumentacyjnym; moduł nie ma zależności JUnit ani automatycznego uruchamiania tych asercji.

<details>
<summary>Rozwiń pełny przykład: JavaPatternsTest</summary>

```java
package pl.jakubtworek.patterns;

import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.patterns.java.JavaPatterns.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class JavaPatternsTest {
    @Test
    void valueObjectHasValueEqualityAndRejectsCurrencyMismatch() {
        assertEquals(new Money(300, "PLN"), new Money(100, "PLN").plus(new Money(200, "PLN")));
        assertThrows(IllegalArgumentException.class, () -> new Money(100, "PLN").plus(new Money(200, "EUR")));
        assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE, "PLN").plus(new Money(1, "PLN")));
    }

    @Test
    void resultRepresentsExpectedFailureWithoutThrowing() {
        assertEquals(new Success<>(5), positiveInteger("5"));
        assertEquals(new Failure<>("not positive"), positiveInteger("0"));
        assertEquals(new Failure<>("not an integer"), positiveInteger("x"));
    }

    @Test
    void specificationComposesIndependentRules() {
        assertTrue(CAN_ORDER.test(new Customer(true, 0)));
        assertFalse(CAN_ORDER.test(new Customer(false, 0)));
        assertFalse(CAN_ORDER.test(new Customer(true, 1)));
    }

    @Test
    void injectedPortCapturesEffectsAndValidationPrecedesSave() {
        var saved = new ArrayList<String>();
        var service = new OrderService(saved::add);
        service.place("o1");
        assertThrows(IllegalArgumentException.class, () -> service.place(" "));
        assertEquals(List.of("o1"), saved);
    }

    @Test
    void executeAroundClosesResourceAfterFailure() {
        var closed = new AtomicBoolean();
        Session session = new Session() {

            public void write(String value) {
                throw new IllegalStateException("disk full");
            }

            public void close() {
                closed.set(true);
            }
        };
        assertThrows(IllegalStateException.class, () -> writeAndClose(session, List.of("data")));
        assertTrue(closed.get());
    }

    @Test
    void functionalCoreProducesImmutableDescriptionOfEffects() {
        assertEquals(new Decision(true, List.of("OrderAccepted")), decide(true));
        assertEquals(List.of("OrderRejected"), decide(false).events());
        assertThrows(UnsupportedOperationException.class, () -> decide(true).events().clear());
        assertDoesNotThrow(() -> NO_AUDIT.record("test"));
    }
}
```

</details>
