# Outbox, Inbox, klucz idempotencji i saga

[Mapa przykładów](../LABORATORIA.md) · [Karty wzorców](../catalog/09-consistency/README.md)

Ten dokument zawiera rozwinięty model i scenariusze jego poprawności w blokach Markdown. Karty katalogu
pozostają punktem wejścia do intuicji, konkretnych zastosowań, kosztu i kontrprzykładów. Modele mają zakres
lokalny i in-memory; trwałość oraz granice rozproszone wymagają osobnego kontraktu.

## Model

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: DeliveryPatterns</summary>

```java
package pl.jakubtworek.patterns.systems;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Jednowątkowe modele awarii. Przypisanie snapshotu modeluje commit, nie trwałą bazę danych.
 */
public final class DeliveryPatterns {
    private DeliveryPatterns() {
    }

    public record Event(String id, String order) {
    }

    private record Store(Set<String> orders, Map<String, Event> pending) {
        private Store {
            orders = Set.copyOf(orders);
            pending = Map.copyOf(pending);
        }
    }

    // TRANSACTIONAL OUTBOX: zamówienie i event są publikowane w jednym lokalnym commicie.
    public static final class Outbox {
        private Store store = new Store(Set.of(), Map.of());

        public void place(String order, Runnable beforeCommit) {
            if (store.orders().contains(order)) {
                throw new IllegalStateException("duplicate order");
            }
            var orders = new HashSet<>(store.orders());
            // Pracujemy na kopiach: modyfikacja wspólnego store przed walidacją złamałaby rollback modelu.
            var pending = new LinkedHashMap<>(store.pending());
            orders.add(order);
            pending.put(order, new Event(order, order));
            // Awaria przed przypisaniem nie może pozostawić połowy zmiany.
            beforeCommit.run();
            store = new Store(orders, pending);
        }

        public boolean hasOrder(String id) {
            return store.orders().contains(id);
        }

        public int pendingCount() {
            return store.pending().size();
        }

        public void relay(Consumer<Event> broker, Runnable afterPublish) {
            for (Event event : new ArrayList<>(store.pending().values())) {
                broker.accept(event);
                // Crash w tym miejscu oznacza ponowne wysłanie eventu.
                afterPublish.run();
                var pending = new LinkedHashMap<>(store.pending());
                pending.remove(event.id());
                store = new Store(store.orders(), pending);
            }
        }
    }

    // INBOX / IDEMPOTENT RECEIVER: marker i skutek należą do tej samej lokalnej transakcji.
    private record InboxState(Set<String> processed, List<String> shipments) {
        private InboxState {
            processed = Set.copyOf(processed);
            shipments = List.copyOf(shipments);
        }
    }

    public static final class Inbox {
        private InboxState state = new InboxState(Set.of(), List.of());

        public void receive(Event event, Runnable beforeCommit) {
            if (state.processed().contains(event.id())) {
                return;
            }
            // Deduplikujemy eventId, a nie orderId ani treść. Różne fakty dla jednego order mogą być legalne.
            var ids = new HashSet<>(state.processed());
            var shipments = new ArrayList<>(state.shipments());
            ids.add(event.id());
            shipments.add(event.order());
            beforeCommit.run();
            state = new InboxState(ids, shipments);
        }

        public List<String> shipments() {
            return state.shipments();
        }
    }

    // IDEMPOTENCY KEY: ponowienie musi mieć ten sam payload; pamiętamy odpowiedź i odcisk żądania.
    public record Receipt(String payload, String response) {
    }

    public static final class IdempotencyKeys {
        private final Map<String, Receipt> receipts = new LinkedHashMap<>();

        public String execute(String key, String payload) {
            Receipt previous = receipts.get(key);
            if (previous != null) {
                // Replay poprzedniego wyniku jest bezpieczny tylko dla tej samej intencji klienta.
                if (!previous.payload().equals(payload)) {
                    throw new IllegalArgumentException("key reused with different payload");
                }
                return previous.response();
            }
            String response = "accepted-" + (receipts.size() + 1);
            receipts.put(key, new Receipt(payload, response));
            return response;
        }
    }

    // SAGA ORCHESTRATION + COMPENSATING TRANSACTION: cofamy tylko zakończone kroki, od końca.
    public record Step(Runnable action, Runnable compensate) {
    }

    public static void saga(List<Step> steps) {
        var completed = new ArrayList<Step>();
        try {
            for (Step step : steps) {
                step.action().run();
                completed.add(step);
            }
        } catch (RuntimeException failure) {
            // Kompensacja ma odrębny wynik. Jej awaria nie może ukryć pierwotnej przyczyny ani pominąć reszty.
            for (int i = completed.size() - 1; i >= 0; i--) {
                try {
                    completed.get(i).compensate().run();
                } catch (RuntimeException compensationFailure) {
                    failure.addSuppressed(compensationFailure);
                }
            }
            throw failure;
        }
    }
}
```

</details>

## Scenariusze poprawności

Scenariusze poprawności w notacji JUnit. Pokazują dane, działanie i oczekiwany stan. Są materiałem
dokumentacyjnym; moduł nie ma zależności JUnit ani automatycznego uruchamiania tych asercji.

<details>
<summary>Rozwiń pełny przykład: DeliveryPatternsTest</summary>

```java
package pl.jakubtworek.patterns;

import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.patterns.systems.DeliveryPatterns.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryPatternsTest {
    private static final Runnable OK = () -> {
    };

    private static final Runnable CRASH = () -> {
        throw new IllegalStateException("crash");
    };

    @Test
    void outboxCommitsBusinessStateAndEventTogether() {
        var outbox = new Outbox();
        assertThrows(IllegalStateException.class, () -> outbox.place("o1", CRASH));
        assertFalse(outbox.hasOrder("o1"));
        assertEquals(0, outbox.pendingCount());
        outbox.place("o1", OK);
        assertTrue(outbox.hasOrder("o1"));
        assertEquals(1, outbox.pendingCount());
        assertThrows(IllegalStateException.class, () -> outbox.place("o1", OK));
    }

    @Test
    void crashAfterPublicationCreatesDuplicateButInboxAppliesEffectOnce() {
        var outbox = new Outbox();
        var inbox = new Inbox();
        var deliveries = new ArrayList<Event>();
        outbox.place("o1", OK);
        assertThrows(IllegalStateException.class, () -> outbox.relay(deliveries::add, CRASH));
        assertEquals(1, outbox.pendingCount());
        outbox.relay(deliveries::add, OK);
        assertEquals(0, outbox.pendingCount());
        assertEquals(2, deliveries.size());
        deliveries.forEach(event -> inbox.receive(event, OK));
        assertEquals(List.of("o1"), inbox.shipments());
    }

    @Test
    void inboxRollsBackMarkerTogetherWithEffect() {
        var inbox = new Inbox();
        var event = new Event("e1", "o1");
        assertThrows(IllegalStateException.class, () -> inbox.receive(event, CRASH));
        assertTrue(inbox.shipments().isEmpty());
        inbox.receive(event, OK);
        assertEquals(List.of("o1"), inbox.shipments());
    }

    @Test
    void idempotencyKeyReplaysResponseAndRejectsDifferentPayload() {
        var keys = new IdempotencyKeys();
        String response = keys.execute("tenant1:k1", "100PLN");
        assertEquals(response, keys.execute("tenant1:k1", "100PLN"));
        assertThrows(IllegalArgumentException.class, () -> keys.execute("tenant1:k1", "200PLN"));
        assertNotEquals(response, keys.execute("tenant2:k1", "100PLN"));
    }

    @Test
    void sagaCompensatesOnlyCompletedStepsInReverseOrder() {
        var log = new ArrayList<String>();
        var steps = List.of(
                new Step(() -> log.add("reserve"), () -> log.add("release")),
                new Step(() -> log.add("charge"), () -> log.add("refund")),
                new Step(CRASH, () -> fail("failed step has not completed")));

        assertThrows(IllegalStateException.class, () -> saga(steps));
        assertEquals(List.of("reserve", "charge", "refund", "release"), log);
    }

    @Test
    void sagaPreservesCompensationFailureAndContinuesRemainingCompensations() {
        var log = new ArrayList<String>();
        var steps = List.of(
                new Step(OK, () -> log.add("released")),
                new Step(OK, CRASH),
                new Step(CRASH, OK));
        var failure = assertThrows(IllegalStateException.class, () -> saga(steps));
        assertEquals(List.of("released"), log);
        assertEquals(1, failure.getSuppressed().length);
    }
}
```

</details>
