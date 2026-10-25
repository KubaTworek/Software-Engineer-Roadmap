# Zamówienie, granica agregatu i Unit of Work

[Lista laboratoriów](README.md) · [Katalog DDD](../catalog/05-domain/README.md) ·
[Persystencja](../catalog/07-persistence/README.md)

**Modele w tym dokumencie:** [OrderWorkflow](01-domain-and-persistence.md#model-orderworkflow),
[UnitOfWorkLab](01-domain-and-persistence.md#model-unitofworklab). **Scenariusze poprawności:**
[dane, operacje i oczekiwane wyniki](01-domain-and-persistence.md#scenariusze-poprawności).

## Problem i podział odpowiedzialności

Zamówienie z dwiema książkami po 1500 groszy i trzema długopisami po 200 ma sumę 3600 groszy, czyli 36 PLN.
Potwierdzenie jest legalne tylko w DRAFT i tylko do wysokości dostarczonego limitu w tej samej walucie.
Niepoprawna decyzja nie może zostawić statusu CONFIRMED, a stary zapis nie może nadpisać nowszego
potwierdzenia.

| Typ | Odpowiedzialność | Czego klient nie może ominąć |
| --- | --- | --- |
| Money | jednostka, dokładne dodawanie i mnożenie | mieszania walut i overflow |
| Line | SKU, dodatnia liczba sztuk i cena jednostkowa | pozycji z ilością 0 |
| Order | suma, stan, legalne przejście i fakt Confirmed | kontroli przez root |
| Orders | zapis i sprawdzenie wersji | konfliktu starego snapshotu |
| ConfirmOrder | koordynacja odczyt → decyzja → zapis | zwracania faktu przed sukcesem zapisu |
| fromLegacyStatus | tłumaczenie kodu ERP | cichego uznania nieznanego kodu za sukces |

Poniżej jest fragment scenariusza poprawności; pełny model i asercje są dalej w tym dokumencie:

```java
var order = Order.draft(new OrderId("o1"), List.of(
        new Line("book", 2, new Money(1500, "PLN")),
        new Line("pen", 3, new Money(200, "PLN"))));

Confirmed event = order.confirm(new Money(4000, "PLN"));

assertEquals(new Money(3600, "PLN"), event.total());
assertEquals(Status.CONFIRMED, order.snapshot(0).status());
```

## Kolejność jest częścią poprawności

Fabryka `draft()` najpierw sprawdza niepustą listę, potem wywołuje `total()`. Dzięki temu nie wydaje obiektu z
mieszanymi walutami lub sumą poza zakresem. `confirm()` sprawdza stan, wylicza sumę i porównuje limit, dopiero
potem zmienia status. Test z limitem 1000 przy sumie 1500 dowodzi pozostania w DRAFT.

Use case nie jest właścicielem tych reguł: pobiera snapshot, odtwarza Order, wywołuje zachowanie i zapisuje
wersję `v + 1` z oczekiwaną wersją `v`. Gdy ktoś wcześniej zapisał `v + 1`, repozytorium odrzuca decyzję z
dawnego stanu. Samo nadanie nowego numeru bez sprawdzenia dotychczasowej wersji nie wystarcza.

Zwrócenie Confirmed następuje po `save()`. Publikacja do brokera wymagałaby jeszcze Outbox w tej samej trwałej
transakcji. Zwykłe wywołanie `publish()` po `save()` nadal pozostawiłoby okno utraty faktu po crash procesu.

## Unit of Work: co musi być atomowe

Drugie studium używa Customer i Row. Row jest niezmiennym rekordem store; Customer ma zachowanie i mutowalny
email. Pierwsze `Session.get(1)` mapuje Row na Customer i zapamiętuje oryginał. Drugie zwraca tę samą
instancję z Identity Map. Inna sesja otrzymuje własny obiekt i własny stan śledzenia zmian.

Commit najpierw buduje mapę dirty obiektów. Store dla każdego porównuje wersję i przygotowuje zastępczy Row w
prywatnej kopii mapy. Dopiero po sprawdzeniu wszystkich rekordów i przejściu hooka awarii publikuje nowy
snapshot jednym przypisaniem. Dlatego konflikt na rekordzie 2 nie pozostawia zapisanego rekordu 1.
Modyfikowanie współdzielonej mapy w tej pętli złamałoby ten niezmiennik.

Zamknięcie sesji bez commit porzuca lokalne zmiany. Jest to rollback modelu: nie wysłano jeszcze nic do bazy.
Zachowana przez klienta referencja Customer może zostać zmieniona po zamknięciu, ale nie jest już śledzona ani
zapisywana.

## Koszt i zakres dowodu

Suma zamówienia kosztuje O(n) pozycji. Identity Map daje oczekiwane O(1) wyszukiwania, a wykrycie dirty O(m)
obiektów sesji. Lokalny commit kopiuje cały store O(N), co upraszcza dowód atomowości, ale nie jest propozycją
implementacji wydajnej bazy. Tożsamość w pamięci nie oznacza izolacji snapshot całej sesji: niezmienione
odczyty nie są walidowane jako read set podczas commit.

Modele są oddzielnymi studiami. Nie implementują JDBC, ORM, flush, fsync, procesów konkurujących w różnych JVM
ani rzeczywistego recovery po restarcie. MemoryOrders jest jednowątkowe, Store w UnitOfWorkLab synchronizuje
publikację.

## Model OrderWorkflow

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: OrderWorkflow</summary>

```java
package pl.jakubtworek.patterns.labs.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregate, Value Object, Domain Factory, Repository, Application Service i ACL
 * w jednym przepływie. Laboratorium jest jednowątkowe; zapis repozytorium obejmuje
 * cały snapshot, ale nie reprezentuje trwałej transakcji SQL.
 */
public final class OrderWorkflow {
    private OrderWorkflow() {
    }

    public record OrderId(String value) {
        public OrderId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Order ID is required");
            }
        }
    }

    public record Money(long cents, String currency) {
        public Money {
            if (cents < 0 || currency == null || !currency.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("Nonnegative amount and currency code required");
            }
        }

        public Money plus(Money other) {
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("Currency mismatch");
            }
            return new Money(Math.addExact(cents, other.cents), currency);
        }

        public Money times(int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Positive quantity required");
            }
            return new Money(Math.multiplyExact(cents, quantity), currency);
        }
    }

    public record Line(String sku, int quantity, Money unitPrice) {
        public Line {
            if (sku == null || sku.isBlank() || quantity <= 0) {
                throw new IllegalArgumentException("SKU and positive quantity required");
            }
            Objects.requireNonNull(unitPrice);
        }

        public Money total() {
            return unitPrice.times(quantity);
        }
    }

    public enum Status {
        DRAFT, CONFIRMED
    }

    public record Confirmed(OrderId orderId, Money total) {
    }

    /**
     * Snapshot jest kontraktem portu tego laboratorium, nie encją konkretnego ORM.
     */
    public record Snapshot(OrderId id, List<Line> lines, Status status, long version) {
        public Snapshot {
            lines = List.copyOf(lines);
            if (version < 0) {
                throw new IllegalArgumentException("Negative version");
            }
        }
    }

    public static final class Order {
        private final OrderId id;
        private final List<Line> lines;
        private Status status;

        private Order(OrderId id, List<Line> lines, Status status) {
            this.id = Objects.requireNonNull(id);
            this.lines = new ArrayList<>(lines);
            this.status = Objects.requireNonNull(status);
        }

        public static Order draft(OrderId id, List<Line> lines) {
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("Order must contain a line");
            }
            Order order = new Order(id, lines, Status.DRAFT);
            // Fabryka nie wydaje obiektu z mieszanymi walutami lub overflow.
            order.total();
            return order;
        }

        public static Order restore(Snapshot snapshot) {
            Order order = draft(snapshot.id(), snapshot.lines());
            order.status = snapshot.status();
            return order;
        }

        public Money total() {
            Money result = new Money(0, lines.getFirst().unitPrice().currency());
            for (Line line : lines) {
                result = result.plus(line.total());
            }
            return result;
        }

        public Confirmed confirm(Money spendingLimit) {
            if (status != Status.DRAFT) {
                throw new IllegalStateException("Only draft can be confirmed");
            }
            Money total = total();
            if (!total.currency().equals(spendingLimit.currency()) || total.cents() > spendingLimit.cents()) {
                throw new IllegalArgumentException("Spending limit exceeded or wrong currency");
            }
            // Wszystkie warunki sprawdzamy przed zmianą stanu. Błąd nie zostawia półprzejścia.
            status = Status.CONFIRMED;
            return new Confirmed(id, total);
        }

        public Snapshot snapshot(long version) {
            return new Snapshot(id, lines, status, version);
        }
    }

    public interface Orders {
        Snapshot get(OrderId id);

        void insert(Snapshot snapshot);

        void save(Snapshot replacement, long expectedVersion);
    }

    public static final class MemoryOrders implements Orders {
        private final Map<OrderId, Snapshot> data = new LinkedHashMap<>();

        @Override
        public Snapshot get(OrderId id) {
            Snapshot result = data.get(id);
            if (result == null) {
                throw new IllegalArgumentException("Unknown order");
            }
            return result;
        }

        @Override
        public void insert(Snapshot snapshot) {
            if (snapshot.version() != 0 || data.containsKey(snapshot.id())) {
                throw new IllegalStateException("Expected a new order at version zero");
            }
            data.put(snapshot.id(), snapshot);
        }

        @Override
        public void save(Snapshot replacement, long expectedVersion) {
            Snapshot current = get(replacement.id());
            long nextVersion = Math.incrementExact(expectedVersion);
            if (current.version() != expectedVersion || replacement.version() != nextVersion) {
                throw new IllegalStateException("Version conflict");
            }
            data.put(replacement.id(), replacement);
        }
    }

    public record ConfirmOrder(Orders orders) {
        public Confirmed execute(OrderId id, Money limit) {
            Snapshot loaded = orders.get(id);
            Order order = Order.restore(loaded);
            Confirmed event = order.confirm(limit);
            orders.save(order.snapshot(Math.incrementExact(loaded.version())), loaded.version());
            // Zwracamy fakt dopiero po udanym zapisie. Trwała publikacja wymaga jeszcze Outbox.
            return event;
        }
    }

    /**
     * ACL jawnie odrzuca nowy kod ERP zamiast zgadywać jego znaczenie.
     */
    public static Status fromLegacyStatus(int code) {
        return switch(code) {
            case 10 ->
                Status.DRAFT;
            case 20 ->
                Status.CONFIRMED;
            default ->
                throw new IllegalArgumentException("Unsupported ERP status: " + code);
        };
    }
}
```

</details>

## Model UnitOfWorkLab

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: UnitOfWorkLab</summary>

```java
package pl.jakubtworek.patterns.labs.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Identity Map, Data Mapper i Unit of Work z atomowym lokalnym zapisem wielu obiektów.
 */
public final class UnitOfWorkLab {
    private UnitOfWorkLab() {
    }

    /**
     * Rekord store nie ma mutowalnych pól ani zachowania domenowego.
     */
    public record Row(long id, String email, long version) {
    }

    public static final class Customer {
        private final long id;
        private String email;

        private Customer(long id, String email) {
            this.id = id;
            changeEmail(email);
        }

        public long id() {
            return id;
        }

        public String email() {
            return email;
        }

        public void changeEmail(String newEmail) {
            if (newEmail == null || !newEmail.contains("@")) {
                throw new IllegalArgumentException("Email address required");
            }
            email = newEmail;
        }
    }

    public static final class Store {
        private Map<Long, Row> rows;

        public Store(Map<Long, String> initial) {
            var prepared = new LinkedHashMap<Long, Row>();
            initial.forEach((id, email) -> prepared.put(id, new Row(id, email, 0)));
            rows = Map.copyOf(prepared);
        }

        public synchronized Row read(long id) {
            Row row = rows.get(id);
            if (row == null) {
                throw new IllegalArgumentException("Unknown customer");
            }
            return row;
        }

        private synchronized void commit(Map<Long, Row> expected, Map<Long, Customer> dirty, Runnable beforeCommit) {
            var next = new LinkedHashMap<>(rows);
            for (var entry : dirty.entrySet()) {
                Row original = expected.get(entry.getKey());
                Row current = read(entry.getKey());
                if (current.version() != original.version()) {
                    throw new IllegalStateException("Concurrent change: " + entry.getKey());
                }
                Customer customer = entry.getValue();
                long nextVersion = Math.incrementExact(current.version());
                next.put(customer.id(), new Row(customer.id(), customer.email(), nextVersion));
            }
            // Do tej chwili nic nie zmieniło store. Konflikt ostatniego wiersza nie zapisuje pierwszego.
            beforeCommit.run();
            rows = Map.copyOf(next);
        }
    }

    public static final class Session implements AutoCloseable {
        private final Store store;
        private final Map<Long, Customer> identityMap = new LinkedHashMap<>();
        private final Map<Long, Row> originals = new LinkedHashMap<>();
        private boolean finished;

        public Session(Store store) {
            this.store = Objects.requireNonNull(store);
        }

        public Customer get(long id) {
            requireOpen();
            return identityMap.computeIfAbsent(id, key -> {
                Row row = store.read(key);
                originals.put(key, row);
                return new Customer(row.id(), row.email());
            });
        }

        public void commit(Runnable beforeCommit) {
            requireOpen();
            var dirty = new LinkedHashMap<Long, Customer>();
            identityMap.forEach((id, customer) -> {
                if (!customer.email().equals(originals.get(id).email())) {
                    dirty.put(id, customer);
                }
            });
            store.commit(originals, dirty, beforeCommit);
            finished = true;
        }

        @Override
        public void close() {
            // Bez commit zmiany żyły wyłącznie w prywatnych obiektach tej sesji.
            finished = true;
            identityMap.clear();
            originals.clear();
        }

        private void requireOpen() {
            if (finished) {
                throw new IllegalStateException("Session already finished");
            }
        }
    }
}
```

</details>

## Scenariusze poprawności

Scenariusze poprawności w notacji JUnit. Pokazują dane, działanie i oczekiwany stan. Są materiałem
dokumentacyjnym; moduł nie ma zależności JUnit ani automatycznego uruchamiania tych asercji.

<details>
<summary>Rozwiń pełny przykład: DomainAndPersistenceLabTest</summary>

```java
package pl.jakubtworek.patterns.labs;

import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.patterns.labs.domain.OrderWorkflow.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.patterns.labs.domain.OrderWorkflow;
import pl.jakubtworek.patterns.labs.persistence.UnitOfWorkLab;

class DomainAndPersistenceLabTest {
    @Test
    void aggregateCalculatesTotalAndConfirmsOnlyOnce() {
        var order = Order.draft(new OrderId("o1"), List.of(
                new Line("book", 2, new Money(1500, "PLN")),
                new Line("pen", 3, new Money(200, "PLN"))));
        Confirmed event = order.confirm(new Money(4000, "PLN"));
        assertEquals(new Money(3600, "PLN"), event.total());
        assertEquals(Status.CONFIRMED, order.snapshot(0).status());
        assertThrows(IllegalStateException.class, () -> order.confirm(new Money(4000, "PLN")));
    }

    @Test
    void failedRuleLeavesAggregateInDraft() {
        var order = Order.draft(new OrderId("o1"), List.of(new Line("book", 1, new Money(1500, "PLN"))));
        assertThrows(IllegalArgumentException.class, () -> order.confirm(new Money(1000, "PLN")));
        assertEquals(Status.DRAFT, order.snapshot(0).status());
        assertDoesNotThrow(() -> order.confirm(new Money(2000, "PLN")));
    }

    @Test
    void factoryRejectsMixedCurrenciesAndOverflowBeforeIssuingAggregate() {
        var id = new OrderId("o1");
        assertThrows(IllegalArgumentException.class, () -> Order.draft(id, List.of()));
        var mixedCurrencies = List.of(
                new Line("a", 1, new Money(100, "PLN")),
                new Line("b", 1, new Money(100, "EUR")));
        var overflowingTotal = List.of(new Line("a", 2, new Money(Long.MAX_VALUE, "PLN")));
        assertThrows(IllegalArgumentException.class, () -> Order.draft(id, mixedCurrencies));
        assertThrows(ArithmeticException.class, () -> Order.draft(id, overflowingTotal));
    }

    @Test
    void useCasePersistsDecisionAndIncrementsVersion() {
        var orders = new MemoryOrders();
        var id = new OrderId("o1");
        orders.insert(Order.draft(id, List.of(new Line("book", 1, new Money(100, "PLN")))).snapshot(0));
        Confirmed event = new ConfirmOrder(orders).execute(id, new Money(200, "PLN"));
        assertEquals(id, event.orderId());
        assertEquals(Status.CONFIRMED, orders.get(id).status());
        assertEquals(1, orders.get(id).version());
    }

    @Test
    void repositoryRejectsASecondWriterUsingTheSameSnapshot() {
        var orders = new MemoryOrders();
        var id = new OrderId("o1");
        orders.insert(Order.draft(id, List.of(new Line("book", 1, new Money(100, "PLN")))).snapshot(0));
        Snapshot stale = orders.get(id);
        new ConfirmOrder(orders).execute(id, new Money(200, "PLN"));
        var staleDecision = new Snapshot(id, stale.lines(), Status.DRAFT, 1);
        assertThrows(IllegalStateException.class, () -> orders.save(staleDecision, stale.version()));
        assertEquals(Status.CONFIRMED, orders.get(id).status());
    }

    @Test
    void antiCorruptionLayerDoesNotGuessUnknownLegacyStatus() {
        assertEquals(Status.DRAFT, OrderWorkflow.fromLegacyStatus(10));
        assertEquals(Status.CONFIRMED, OrderWorkflow.fromLegacyStatus(20));
        assertThrows(IllegalArgumentException.class, () -> OrderWorkflow.fromLegacyStatus(30));
    }

    @Test
    void identityMapIsScopedToOneSession() {
        var store = new UnitOfWorkLab.Store(Map.of(1L, "old@example.test"));
        try (var first = new UnitOfWorkLab.Session(store);
            var second = new UnitOfWorkLab.Session(store)) {
            assertSame(first.get(1), first.get(1));
            assertNotSame(first.get(1), second.get(1));
        }
    }

    @Test
    void uncommittedChangesAreNotVisibleAndCloseDiscardsThem() {
        var store = new UnitOfWorkLab.Store(Map.of(1L, "old@example.test"));
        try (var session = new UnitOfWorkLab.Session(store)) {
            session.get(1).changeEmail("new@example.test");
            assertEquals("old@example.test", store.read(1).email());
        }
        assertEquals("old@example.test", store.read(1).email());
    }

    @Test
    void unitOfWorkCommitsDirtyObjectsButDoesNotRewriteUnchangedRows() {
        var store = new UnitOfWorkLab.Store(Map.of(1L, "a@example.test", 2L, "b@example.test"));
        try (var session = new UnitOfWorkLab.Session(store)) {
            session.get(1).changeEmail("new@example.test");
            session.get(2);
            session.commit(() -> {
            });
            assertThrows(IllegalStateException.class, () -> session.get(1));
        }
        assertEquals(new UnitOfWorkLab.Row(1, "new@example.test", 1), store.read(1));
        assertEquals(0, store.read(2).version());
    }

    @Test
    void conflictOnOneRowRollsBackOtherRowsOfTheSameUnitOfWork() {
        var store = new UnitOfWorkLab.Store(Map.of(1L, "a@example.test", 2L, "b@example.test"));
        try (var stale = new UnitOfWorkLab.Session(store);
            var winner = new UnitOfWorkLab.Session(store)) {
            stale.get(1).changeEmail("lost-a@example.test");
            stale.get(2).changeEmail("lost-b@example.test");
            winner.get(2).changeEmail("winner@example.test");
            winner.commit(() -> {
            });
            assertThrows(IllegalStateException.class, () -> stale.commit(() -> {
            }));
        }
        // Gdyby store modyfikował wiersze w miejscu, wiersz 1 pozostałby częściowo zapisany.
        assertEquals("a@example.test", store.read(1).email());
        assertEquals("winner@example.test", store.read(2).email());
    }

    @Test
    void crashBeforeCommitDoesNotPublishPreparedRows() {
        var store = new UnitOfWorkLab.Store(Map.of(1L, "old@example.test"));
        try (var session = new UnitOfWorkLab.Session(store)) {
            session.get(1).changeEmail("new@example.test");
            assertThrows(IllegalStateException.class, () -> session.commit(() -> {
                throw new IllegalStateException("Injected failure before snapshot swap");
            }));
        }
        assertEquals(new UnitOfWorkLab.Row(1, "old@example.test", 0), store.read(1));
    }
}
```

</details>
