# Decisions

## 0001 — Start as a plain layered monolith

The project starts as a regular Spring Boot monolith with controller, service and repository layers.

Reason: the learning goal is to understand concurrency, transactions, JPA, SQL performance, JVM behavior and Spring internals without hiding basic problems behind architectural ceremony.

## 0002 — PostgreSQL is the source of truth

Reservations and capacity are stored in PostgreSQL.

Reason: limited capacity and overselling are transactional problems. The base project should first solve them with relational database mechanisms before introducing cache, NoSQL or asynchronous processing.

## 0003 — Atomic SQL update for reservation capacity

The base reservation flow decrements capacity with a guarded update.

Reason: it is a simple and robust baseline against overselling. Later stages should still implement naive and lock-based variants for educational comparison.

## Etap 2 — concurrency strategy

Dla produkcyjnego flow rezerwacji preferujemy atomowy update SQL:

```sql
UPDATE capacity_pools
SET available_capacity = available_capacity - 1
WHERE event_id = ?
  AND available_capacity > 0;
```

Powód: problem dostępności jest prostym licznikiem, więc warunek biznesowy i modyfikacja mogą być jedną operacją bazodanową. To ogranicza ryzyko race condition bez rozbudowanej logiki blokowania w aplikacji.

Pozostałe strategie są zostawione w kodzie jako materiał edukacyjny:

- `NaiveReservationService` pokazuje overselling i lost update,
- `SynchronizedReservationService` pokazuje lokalną ochronę w jednej JVM,
- `OptimisticLockingReservationService` pokazuje użycie `@Version` i retry,
- `PessimisticLockingReservationService` pokazuje odpowiednik `SELECT ... FOR UPDATE`,
- `AtomicSqlReservationService` pokazuje preferowaną strategię dla tego projektu.

## Stage 3: async processing stays inside the monolith

For Stage 3 the project uses in-process asynchronous processing with `ThreadPoolExecutor`, `ScheduledExecutorService` and `CompletableFuture`.

Decision:

- keep one Spring Boot application,
- keep one relational database,
- do not introduce Kafka/RabbitMQ yet,
- model async side effects through local services and database records,
- use explicit executor beans instead of the common pool.

Reason:

This stage is about Java concurrency and async programming fundamentals, not distributed systems. Adding a broker now would hide the core learning goals behind infrastructure.

Trade-off:

In-process async work can be lost if the JVM crashes after the reservation is confirmed but before side effects finish. That is acceptable for this educational stage. A production-grade version would likely use the transactional outbox pattern.

## Stage 3: payment timeout status

Slow or technically failing payment validation marks the reservation as `PAYMENT_TIMEOUT`.

Reason:

The system should not confirm a reservation when payment validation is unknown. `PAYMENT_TIMEOUT` makes the uncertain state explicit and testable.

Trade-off:

In a larger production model, payment state should probably be separated from reservation state. Here it is intentionally kept simpler because the project is still a basic monolith.

## Stage 4 — Spring proxy pitfalls

Decision: examples of broken Spring behavior are implemented in `service.pitfall` and exposed under `/api/spring-pitfalls`.

Reason: these examples are intentionally educational. They should not pollute the main reservation flow. The main business flow should stay boring and predictable, while Stage 4 demonstrates proxy boundaries, transaction boundaries, lazy loading failures and AOP behavior in isolation.

Important trade-off: self-injection through `ObjectProvider` is used only to demonstrate that proxy invocation changes behavior. It is not a default design recommendation.

## Stage 5 — SQL i performance

### Decyzja: pełny dataset nie jest częścią zwykłych testów

Pełny zbiór 100 000 eventów i 1 000 000 rezerwacji jest generowany tylko przez profil `performance-seed`. Zwykłe testy mają sprawdzać poprawność zapytań i endpointów, a nie benchmarkować bazę danych. Benchmarki i `EXPLAIN ANALYZE` są osobnym, świadomym krokiem wykonywanym na PostgreSQL.

### Decyzja: indeksy są dobierane pod access pattern

Dla event search używamy indeksu `(city, category, starts_at)`, bo zapytanie filtruje po `city` i `category`, a potem robi zakres/sortowanie po `starts_at`. Dla widoku rezerwacji organizacji używamy `(organization_id, status, created_at DESC)`. Dla historii klienta używamy `(customer_id, created_at DESC, id DESC)`.

### Decyzja: offset pagination zostaje obok keyset pagination

Offset pagination jest prosta dla UI i pozwala skakać do strony N, ale źle skaluje się dla głębokich stron. Keyset pagination jest mniej wygodna, lecz zwykle stabilniejsza wydajnościowo dla feedów i historii.

### Decyzja: N+1 pokazujemy celowo

Endpoint `n-plus-one` zostaje jako edukacyjna pułapka. Produkcyjny wariant powinien używać DTO projection, `fetch join` albo `@EntityGraph`, zależnie od konkretnego przypadku.
