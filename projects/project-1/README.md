# Booking & Capacity Platform

> Projekt edukacyjno-portfolio dla backend developera Java/Spring, którego celem jest przećwiczenie tematów z Etapu 1: Java Concurrency, JVM i profilowanie, Spring pod maską, bazy danych i performance, NoSQL oraz clean code i testowalność.

## Spis treści

1. [Cel projektu](#cel-projektu)
2. [Problem biznesowy](#problem-biznesowy)
3. [Zakres funkcjonalny](#zakres-funkcjonalny)
4. [Zakres techniczny](#zakres-techniczny)
5. [Architektura](#architektura)
6. [Model domenowy](#model-domenowy)
7. [Główne przypadki użycia](#główne-przypadki-użycia)
8. [API](#api)
9. [Transakcje i concurrency](#transakcje-i-concurrency)
10. [Spring pod maską](#spring-pod-maską)
11. [SQL i wydajność bazy danych](#sql-i-wydajność-bazy-danych)
12. [JVM, profilowanie i benchmarki](#jvm-profilowanie-i-benchmarki)
13. [Asynchroniczność](#asynchroniczność)
14. [Security i autoryzacja](#security-i-autoryzacja)
15. [NoSQL i cache](#nosql-i-cache)
16. [Testy](#testy)
17. [Struktura repozytorium](#struktura-repozytorium)
18. [Lokalne uruchomienie](#lokalne-uruchomienie)
19. [Migracje bazy danych](#migracje-bazy-danych)
20. [Dane testowe](#dane-testowe)
21. [Dokumentacja decyzji technicznych](#dokumentacja-decyzji-technicznych)
22. [Plan realizacji](#plan-realizacji)
23. [Definition of Done](#definition-of-done)
24. [Antywzorce, które projekt ma ujawnić](#antywzorce-które-projekt-ma-ujawnić)
25. [Rozszerzenia opcjonalne](#rozszerzenia-opcjonalne)

---

## Cel projektu

**Booking & Capacity Platform** to system rezerwacji ograniczonych zasobów. Projekt ma jeden nadrzędny cel: nauczyć świadomego projektowania backendu, który zachowuje poprawność pod obciążeniem, jest mierzalny wydajnościowo, testowalny i odporny na typowe błędy związane z transakcjami, współbieżnością, Springiem, JPA oraz bazami danych.

Projekt nie ma być tylko kolejną aplikacją CRUD. Ma być kontrolowanym laboratorium problemów, które występują w realnych systemach produkcyjnych.

Po ukończeniu projektu powinieneś umieć:

- zreprodukować i naprawić race condition,
- wyjaśnić lost update, overselling i check-then-act,
- dobrać strategię blokowania w Javie i w bazie danych,
- świadomie używać `@Transactional`,
- rozumieć granice proxy w Springu,
- diagnozować problemy JPA: N+1, lazy loading, flush, dirty checking,
- analizować zapytania przez `EXPLAIN ANALYZE`,
- dobrać indeks do konkretnego access patternu,
- porównać offset pagination i keyset pagination,
- używać `ExecutorService` i `CompletableFuture` bez wycieków wątków,
- profilować aplikację przy użyciu JFR lub profilera,
- mierzyć kod przez JMH bez podstawowych błędów,
- projektować autoryzację opartą o dane, nie tylko role,
- refaktoryzować kod bez przepisywania systemu od zera.

---

## Problem biznesowy

System obsługuje rezerwacje zasobów o ograniczonej dostępności. Przykłady zasobów:

- bilety na wydarzenie,
- miejsca na konferencji,
- pokoje hotelowe,
- sloty wizyt,
- ograniczona pula produktów,
- miejsca w grupie szkoleniowej.

Najważniejsza reguła biznesowa:

> System nigdy nie może potwierdzić większej liczby rezerwacji niż dostępna pojemność zasobu.

Przykład:

- wydarzenie ma 10 dostępnych miejsc,
- 100 użytkowników jednocześnie próbuje zrobić rezerwację,
- dokładnie 10 rezerwacji może zostać zaakceptowanych,
- pozostałe muszą zostać odrzucone albo oznaczone jako oczekujące na zwolnienie miejsca.

Ten problem wymusza realne decyzje techniczne. Naiwna implementacja typu `if available > 0 then available--` jest błędna pod współbieżnym obciążeniem. Projekt ma pokazać ten błąd, zmierzyć go i naprawić kilkoma strategiami.

---

## Zakres funkcjonalny

### MVP

Minimalna wersja systemu powinna umożliwiać:

- utworzenie organizacji,
- utworzenie wydarzenia,
- zdefiniowanie puli miejsc,
- utworzenie klienta,
- wykonanie rezerwacji,
- potwierdzenie rezerwacji,
- anulowanie rezerwacji,
- automatyczne wygaśnięcie rezerwacji,
- pobranie szczegółów wydarzenia,
- pobranie rezerwacji użytkownika,
- pobranie listy rezerwacji dla organizacji.

### Funkcje rozszerzone

Docelowo system powinien obsługiwać:

- wiele organizacji,
- wielu użytkowników w organizacji,
- role i uprawnienia,
- tenant boundary,
- płatności symulowane przez zewnętrznego providera,
- powiadomienia mailowe,
- audyt operacji,
- cache dla wybranych odczytów,
- read model w NoSQL,
- raporty i statystyki,
- testy wydajnościowe,
- benchmarki JMH,
- profilowanie JVM.

---

## Zakres techniczny

Projekt celowo obejmuje szeroki zakres tematów backendowych.

### Java Concurrency

Do przećwiczenia:

- `ExecutorService`,
- `ThreadPoolExecutor`,
- fixed/cached/scheduled thread pools,
- kolejki zadań,
- strategie odrzucania zadań,
- `shutdown`, `shutdownNow`, `awaitTermination`,
- `CompletableFuture`,
- fan-out/fan-in,
- timeouty,
- fallbacki,
- obsługa wyjątków,
- `Future.cancel`,
- `InterruptedException`,
- race condition,
- lost update,
- check-then-act,
- deadlock,
- visibility,
- `volatile`,
- happens-before,
- `ConcurrentHashMap`,
- thread confinement,
- testowanie kodu współbieżnego.

### JVM i performance

Do przećwiczenia:

- heap vs stack,
- `-Xms`, `-Xmx`,
- G1 vs ZGC,
- allocation rate,
- allocation churn,
- escape analysis,
- CPU-bound vs IO-bound,
- koszt wątków,
- context switching,
- lock contention,
- false sharing,
- stream vs loop,
- `ArrayList` vs `LinkedList`,
- `BigDecimal` vs `long`,
- object pooling,
- polimorfizm i JIT,
- benchmarki JMH.

### Spring

Do przećwiczenia:

- lifecycle beanów,
- dependency injection,
- singleton scope,
- `@Configuration`,
- `@Bean`,
- component scanning,
- auto-configuration,
- Spring AOP,
- proxy boundaries,
- `@Transactional`,
- propagation,
- isolation levels,
- rollback rules,
- self-invocation,
- lazy loading,
- JPA persistence context,
- dirty checking,
- flush vs commit,
- exception handling,
- `@ControllerAdvice`,
- Spring Security,
- `@PreAuthorize`,
- testy warstwowe Springa.

### Bazy danych

Do przećwiczenia:

- indeksy B-tree,
- selectivity,
- composite indexes,
- kolejność kolumn w indeksie,
- `EXPLAIN`,
- `EXPLAIN ANALYZE`,
- sequential scan,
- index scan,
- bitmap index scan,
- nested loop,
- hash join,
- sort,
- transakcje SQL,
- isolation levels,
- optimistic locking,
- pessimistic locking,
- row-level locks,
- lock wait,
- deadlock w bazie,
- N+1,
- fetch join,
- entity graph,
- batch fetching,
- offset pagination,
- keyset pagination,
- connection pool,
- read-heavy vs write-heavy workload.

### NoSQL

Do przećwiczenia:

- Redis jako key-value store,
- TTL,
- cache,
- ograniczenia zapytań po kluczu,
- MongoDB jako dokumentowy read model,
- denormalizacja,
- modeling pod access pattern,
- eventual consistency,
- read-your-writes.

### Clean Code i testowalność

Do przećwiczenia:

- refaktoryzacja bez rewrite'u,
- separacja domeny, aplikacji, infrastruktury i adapterów,
- unit testy bez Spring Context,
- testy integracyjne,
- testy negatywne,
- testy regresyjne,
- mockowanie granic systemu zamiast mockowania wszystkiego.

---

## Architektura

Projekt powinien być rozwijany w kierunku architektury modularnej, z wyraźnym oddzieleniem domeny od infrastruktury.

Rekomendowany styl:

- modular monolith,
- warstwy inspirowane Clean Architecture / Hexagonal Architecture,
- domena bez zależności od Springa,
- przypadki użycia w warstwie application,
- adaptery wejściowe i wyjściowe,
- infrastruktura jako szczegół implementacyjny.

### Główne warstwy

```text
adapters/
  web/
  scheduler/
  security/

application/
  reservation/
  event/
  payment/
  organization/
  security/

domain/
  reservation/
  event/
  capacity/
  payment/
  organization/
  user/

infrastructure/
  persistence/
  mail/
  payment/
  cache/
  nosql/
  audit/

config/
```

### Zasady zależności

Warstwy powinny zależeć w jedną stronę:

```text
adapters -> application -> domain
infrastructure -> application/domain przez porty
```

Domena nie powinna zależeć od:

- Springa,
- JPA,
- Hibernate,
- kontrolerów,
- DTO API,
- bazy danych,
- klienta HTTP,
- cache.

To oznacza, że reguły takie jak “nie można zarezerwować więcej miejsc niż dostępne” powinny być możliwe do testowania bez uruchamiania Spring Context.

---

## Model domenowy

### Organization

Reprezentuje tenant lub właściciela wydarzeń.

Przykładowe pola:

```text
Organization
- id
- name
- status
- createdAt
```

Reguły:

- użytkownik z jednej organizacji nie powinien mieć dostępu do danych innej organizacji,
- manager może zarządzać tylko wydarzeniami swojej organizacji,
- admin organizacji może zarządzać użytkownikami tylko w ramach swojego tenant boundary.

### User

Reprezentuje użytkownika systemu.

Przykładowe pola:

```text
User
- id
- organizationId
- email
- role
- status
- createdAt
```

Role:

- `CUSTOMER`,
- `EVENT_MANAGER`,
- `ORG_ADMIN`,
- `HR`,
- `SUPPORT`.

### Event

Reprezentuje wydarzenie lub zasób, na który można wykonać rezerwację.

Przykładowe pola:

```text
Event
- id
- organizationId
- name
- city
- category
- startTime
- endTime
- status
- createdAt
```

Reguły:

- tylko manager organizacji może tworzyć i edytować wydarzenia,
- wydarzenie może mieć jedną lub wiele pul miejsc,
- wydarzenie może zostać opublikowane dopiero po zdefiniowaniu pojemności.

### CapacityPool

Reprezentuje ograniczoną pulę zasobu.

Przykładowe pola:

```text
CapacityPool
- id
- eventId
- totalCapacity
- availableCapacity
- version
- createdAt
- updatedAt
```

Reguły:

- `availableCapacity` nie może spaść poniżej zera,
- `totalCapacity` nie może być mniejsze niż liczba potwierdzonych rezerwacji,
- zmiana dostępności musi być odporna na równoległe requesty.

### Reservation

Reprezentuje rezerwację użytkownika.

Przykładowe pola:

```text
Reservation
- id
- eventId
- capacityPoolId
- customerId
- organizationId
- status
- expiresAt
- confirmedAt
- cancelledAt
- createdAt
- updatedAt
```

Statusy:

```text
PENDING
CONFIRMED
EXPIRED
CANCELLED
PAYMENT_TIMEOUT
FAILED
```

Reguły:

- rezerwacja `PENDING` blokuje miejsce przez określony czas,
- rezerwacja `CONFIRMED` trwale zużywa miejsce,
- rezerwacja `EXPIRED` powinna zwolnić miejsce,
- rezerwacja `CANCELLED` powinna zwolnić miejsce, jeśli wcześniej blokowała miejsce,
- statusu nie można zmieniać dowolnie.

Przykładowe dozwolone przejścia:

```text
PENDING -> CONFIRMED
PENDING -> EXPIRED
PENDING -> CANCELLED
CONFIRMED -> CANCELLED
```

Przykładowe przejścia niedozwolone:

```text
EXPIRED -> CONFIRMED
CANCELLED -> CONFIRMED
FAILED -> CONFIRMED
```

### Payment

Reprezentuje symulowaną płatność.

Przykładowe pola:

```text
Payment
- id
- reservationId
- providerReference
- status
- amount
- currency
- createdAt
- updatedAt
```

Statusy:

```text
INITIATED
AUTHORIZED
CONFIRMED
FAILED
TIMEOUT
```

Reguły:

- potwierdzenie płatności może potwierdzić rezerwację,
- timeout płatności nie powinien zostawiać systemu w niespójnym stanie,
- zewnętrzny provider powinien być mockowany za granicą systemu.

### AuditLog

Reprezentuje zapis istotnych zdarzeń.

Przykładowe pola:

```text
AuditLog
- id
- actorId
- organizationId
- action
- resourceType
- resourceId
- createdAt
- metadata
```

Reguły:

- operacje biznesowo istotne powinny być audytowane,
- audyt nie powinien blokować głównego requestu, jeśli nie jest krytyczny,
- błąd zapisu audytu musi być świadomą decyzją: albo fail-fast, albo best-effort.

---

## Główne przypadki użycia

### Create Event

Aktor: `EVENT_MANAGER` albo `ORG_ADMIN`.

Opis:

1. Użytkownik wysyła dane wydarzenia.
2. System waliduje request.
3. System sprawdza uprawnienia.
4. System tworzy wydarzenie.
5. System tworzy domyślną pulę miejsc albo wymaga osobnego requestu.
6. System zapisuje audyt.

Ryzyka techniczne:

- zbyt gruby controller,
- mieszanie walidacji technicznej i domenowej,
- brak tenant boundary,
- zbyt szeroka transakcja.

### Reserve Capacity

Aktor: `CUSTOMER`.

Opis:

1. Użytkownik próbuje zarezerwować miejsce.
2. System sprawdza, czy wydarzenie istnieje i jest aktywne.
3. System sprawdza dostępność.
4. System atomowo zmniejsza dostępność.
5. System tworzy rezerwację `PENDING`.
6. System ustawia `expiresAt`.
7. System wysyła powiadomienie asynchronicznie.

Najważniejsze ryzyko:

- overselling przy dużej liczbie równoległych requestów.

To jest centralny przypadek użycia całego projektu.

### Confirm Reservation

Aktor: `CUSTOMER` albo system po pozytywnej płatności.

Opis:

1. System znajduje rezerwację.
2. System sprawdza, czy rezerwacja jest `PENDING`.
3. System sprawdza, czy nie wygasła.
4. System potwierdza płatność.
5. System zmienia status na `CONFIRMED`.
6. System zapisuje audyt.
7. System wysyła potwierdzenie.

Ryzyka techniczne:

- timeout zewnętrznego providera,
- częściowy sukces,
- retry,
- idempotencja,
- błędne granice transakcji.

### Cancel Reservation

Aktor: `CUSTOMER`, `EVENT_MANAGER`, `ORG_ADMIN` albo system.

Opis:

1. System znajduje rezerwację.
2. System sprawdza uprawnienia.
3. System sprawdza status.
4. System anuluje rezerwację.
5. System zwalnia miejsce, jeśli powinno zostać zwolnione.
6. System zapisuje audyt.

Ryzyka techniczne:

- podwójne zwolnienie miejsca,
- race condition między anulowaniem a potwierdzaniem,
- brak idempotencji.

### Expire Reservations

Aktor: scheduler.

Opis:

1. Scheduler znajduje rezerwacje `PENDING`, których `expiresAt` jest w przeszłości.
2. System oznacza je jako `EXPIRED`.
3. System zwalnia zablokowane miejsca.
4. System zapisuje audyt.

Ryzyka techniczne:

- kilka instancji aplikacji uruchamia scheduler jednocześnie,
- deadlock w bazie,
- długie transakcje,
- batch processing bez limitu,
- brak paginacji.

---

## API

API powinno być projektowane jako stabilny kontrakt, nie jako bezpośrednie wystawienie encji JPA.

### Events

#### Create event

```http
POST /api/events
Content-Type: application/json
Authorization: Bearer <token>
```

Request:

```json
{
  "organizationId": "7b7b7d7c-0f7e-4b42-8c68-8e75d95c1111",
  "name": "Java Backend Performance Workshop",
  "city": "Warsaw",
  "category": "workshop",
  "startTime": "2026-06-01T10:00:00Z",
  "endTime": "2026-06-01T18:00:00Z",
  "capacity": 100
}
```

Response:

```json
{
  "id": "1a1b2c3d-0000-4000-9000-111111111111",
  "organizationId": "7b7b7d7c-0f7e-4b42-8c68-8e75d95c1111",
  "name": "Java Backend Performance Workshop",
  "city": "Warsaw",
  "category": "workshop",
  "startTime": "2026-06-01T10:00:00Z",
  "endTime": "2026-06-01T18:00:00Z",
  "capacity": 100,
  "availableCapacity": 100,
  "status": "DRAFT"
}
```

#### Get event

```http
GET /api/events/{eventId}
```

#### Search events

```http
GET /api/events?city=Warsaw&category=workshop&from=2026-06-01&limit=20
```

Ten endpoint służy później do ćwiczenia indeksów, selektywności i paginacji.

---

### Reservations

#### Create reservation

```http
POST /api/events/{eventId}/reservations
Content-Type: application/json
Authorization: Bearer <token>
```

Request:

```json
{
  "customerId": "2c2d2e2f-0000-4000-9000-222222222222"
}
```

Response:

```json
{
  "id": "3d3e3f40-0000-4000-9000-333333333333",
  "eventId": "1a1b2c3d-0000-4000-9000-111111111111",
  "customerId": "2c2d2e2f-0000-4000-9000-222222222222",
  "status": "PENDING",
  "expiresAt": "2026-06-01T09:15:00Z"
}
```

Możliwe błędy:

- `EVENT_NOT_FOUND`,
- `EVENT_NOT_ACTIVE`,
- `CAPACITY_EXHAUSTED`,
- `CUSTOMER_NOT_FOUND`,
- `ACCESS_DENIED`.

#### Confirm reservation

```http
POST /api/reservations/{reservationId}/confirm
```

#### Cancel reservation

```http
POST /api/reservations/{reservationId}/cancel
```

#### Get reservation

```http
GET /api/reservations/{reservationId}
```

#### List organization reservations

```http
GET /api/organizations/{organizationId}/reservations?status=CONFIRMED&limit=20&cursor=...
```

Ten endpoint powinien docelowo używać keyset pagination.

---

## Error contract

Błędy API powinny mieć spójny format.

Przykład:

```json
{
  "code": "CAPACITY_EXHAUSTED",
  "message": "No capacity available for this event.",
  "correlationId": "8f2d1e4c-1234-4567-9876-abcdefabcdef",
  "details": {
    "eventId": "1a1b2c3d-0000-4000-9000-111111111111"
  }
}
```

Zasady:

- komunikaty błędów nie powinny ujawniać szczegółów technicznych,
- wyjątki domenowe powinny mapować się na kontrolowane statusy HTTP,
- błędy walidacyjne powinny wskazywać konkretne pola,
- błędy techniczne powinny mieć correlation ID,
- stack trace nie może wyciekać do klienta.

Przykładowe kody błędów:

```text
VALIDATION_ERROR
EVENT_NOT_FOUND
EVENT_NOT_ACTIVE
RESERVATION_NOT_FOUND
RESERVATION_ALREADY_CONFIRMED
RESERVATION_EXPIRED
CAPACITY_EXHAUSTED
PAYMENT_TIMEOUT
ACCESS_DENIED
CONFLICT
INTERNAL_ERROR
```

---

## Transakcje i concurrency

To najważniejszy technicznie obszar projektu.

### Naiwna implementacja

Na początku celowo można zaimplementować błędne rozwiązanie:

```java
if (capacityPool.getAvailableCapacity() > 0) {
    capacityPool.decreaseAvailableCapacity();
    reservationRepository.save(reservation);
}
```

To wygląda poprawnie, ale przy wielu równoległych requestach może prowadzić do lost update i oversellingu.

### Test reprodukujący race condition

Wymagany test:

- pula miejsc: 10,
- liczba równoległych prób rezerwacji: 100,
- oczekiwany wynik: maksymalnie 10 potwierdzonych lub pending rezerwacji,
- naiwny kod powinien czasami test oblać.

Przykładowe narzędzia:

- `ExecutorService`,
- `CountDownLatch`,
- `CyclicBarrier`,
- `AtomicInteger`,
- test integracyjny z prawdziwą bazą danych.

Szkic testu:

```java
@Test
void shouldNotOversellCapacityUnderConcurrentRequests() throws Exception {
    int capacity = 10;
    int attempts = 100;

    UUID eventId = createEventWithCapacity(capacity);

    ExecutorService executor = Executors.newFixedThreadPool(20);
    CountDownLatch ready = new CountDownLatch(attempts);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(attempts);

    AtomicInteger successCount = new AtomicInteger();

    for (int i = 0; i < attempts; i++) {
        executor.submit(() -> {
            try {
                ready.countDown();
                start.await();
                reservationFacade.reserve(eventId, randomCustomerId());
                successCount.incrementAndGet();
            } catch (CapacityExhaustedException ignored) {
                // expected for some callers
            } finally {
                done.countDown();
            }
        });
    }

    ready.await();
    start.countDown();
    done.await();

    assertThat(successCount.get()).isLessThanOrEqualTo(capacity);
    assertThat(countReservations(eventId)).isLessThanOrEqualTo(capacity);
}
```

### Strategie naprawy

Projekt powinien porównać co najmniej cztery strategie.

#### 1. `synchronized`

Zalety:

- proste do zrozumienia,
- działa w jednej instancji JVM,
- dobre jako ćwiczenie podstaw synchronizacji.

Wady:

- nie działa poprawnie przy wielu instancjach aplikacji,
- nie chroni przed równoległymi zmianami z innych procesów,
- może obniżyć throughput,
- łatwo zrobić zbyt szeroki lock.

Wniosek:

- dobre edukacyjnie,
- słabe jako główne rozwiązanie produkcyjne dla aplikacji wieloinstancyjnej.

#### 2. Optimistic locking

Mechanizm:

- encja ma pole `@Version`,
- równoległa aktualizacja tej samej wersji powoduje konflikt,
- konflikt można obsłużyć przez retry albo błąd biznesowy.

Zalety:

- dobre przy umiarkowanej konkurencji,
- brak blokowania rekordu na czas transakcji,
- dobrze pasuje do JPA.

Wady:

- przy bardzo wysokiej konkurencji może generować wiele retry,
- trzeba świadomie obsłużyć `OptimisticLockException`,
- retry musi mieć limit.

#### 3. Pessimistic locking

Mechanizm:

- rekord puli miejsc jest blokowany w bazie,
- inne transakcje czekają lub dostają timeout.

Przykład:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from CapacityPoolEntity p where p.id = :id")
Optional<CapacityPoolEntity> findByIdForUpdate(UUID id);
```

Zalety:

- proste mentalnie,
- dobre przy wysokiej konkurencji na ten sam zasób,
- baza pilnuje kolejności zmian.

Wady:

- może obniżyć throughput,
- może powodować lock wait,
- może prowadzić do deadlocków przy złej kolejności blokad,
- wymaga monitorowania.

#### 4. Atomowy update SQL

Mechanizm:

```sql
UPDATE capacity_pool
SET available_capacity = available_capacity - 1
WHERE id = :id
  AND available_capacity > 0;
```

Następnie aplikacja sprawdza liczbę zmienionych wierszy.

Jeżeli `updatedRows == 1`, rezerwacja może zostać utworzona.

Jeżeli `updatedRows == 0`, pojemność została wyczerpana.

Zalety:

- bardzo dobre dla problemu zmniejszania licznika,
- krótka operacja w bazie,
- brak klasycznego check-then-act po stronie aplikacji,
- dobre pod obciążeniem.

Wady:

- logika częściowo trafia do SQL,
- trzeba uważać na spójność z utworzeniem rezerwacji,
- nadal wymaga dobrej transakcji.

Rekomendacja dla tego projektu:

> Jako docelowe rozwiązanie dla rezerwacji miejsc warto zaimplementować atomowy update SQL, a obok niego pokazać wariant optimistic i pessimistic locking jako porównanie trade-offów.

---

## Deadlocki

Projekt powinien zawierać kontrolowany przykład deadlocka.

### Deadlock w Javie

Przykład edukacyjny:

- dwa locki: `lockA`, `lockB`,
- wątek 1 bierze `lockA`, potem `lockB`,
- wątek 2 bierze `lockB`, potem `lockA`,
- oba wątki czekają na siebie.

Naprawa:

- zawsze zdobywać locki w tej samej kolejności,
- użyć `tryLock` z timeoutem,
- ograniczyć sekcję krytyczną,
- unikać wielu locków, jeśli można uprościć model.

### Deadlock w bazie danych

Przykład biznesowy:

- transakcja 1 aktualizuje rezerwację A, potem B,
- transakcja 2 aktualizuje rezerwację B, potem A,
- baza wykrywa deadlock i przerywa jedną transakcję.

Naprawa:

- sortować ID przed aktualizacją,
- aktualizować rekordy w deterministycznej kolejności,
- trzymać transakcje krótkie,
- unikać mieszania wielu agregatów w jednej transakcji,
- obsłużyć retry tylko dla bezpiecznych operacji.

---

## Spring pod maską

Projekt powinien mieć osobny pakiet lub moduł `spring-pitfalls`, gdzie celowo pokazujesz typowe błędy.

### Self-invocation i `@Transactional`

Błędny przykład:

```java
@Service
class ReservationService {

    public void confirm(UUID reservationId) {
        this.changeStatusInTransaction(reservationId);
    }

    @Transactional
    public void changeStatusInTransaction(UUID reservationId) {
        // expected transaction, but self-invocation bypasses proxy
    }
}
```

Problem:

- Spring AOP działa przez proxy,
- wywołanie przez `this` omija proxy,
- adnotacja może nie zostać zastosowana tak, jak oczekujesz.

Naprawy:

- przenieść metodę transakcyjną do osobnego beana,
- wywołać metodę przez proxy,
- zaprojektować przypadek użycia z właściwą granicą transakcji,
- nie rozbijać sztucznie transakcji na prywatne/self-invoked metody.

### LazyInitializationException

Celowy scenariusz:

- controller zwraca encję JPA,
- transakcja już się zakończyła,
- serializacja JSON próbuje wejść w lazy relation,
- pojawia się `LazyInitializationException`.

Naprawy do porównania:

- DTO projection,
- fetch join,
- entity graph,
- właściwa granica transakcji,
- unikanie zwracania encji JPA z API.

### N+1

Celowy scenariusz:

- endpoint pobiera listę wydarzeń,
- dla każdego wydarzenia dociąga organizację lub rezerwacje,
- liczba zapytań rośnie liniowo.

Naprawy:

- `join fetch`,
- `@EntityGraph`,
- batch fetching,
- projection query,
- oddzielny read model.

### AOP i proxy boundaries

Dodaj własną adnotację:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Measured {
}
```

Dodaj aspekt:

```java
@Aspect
@Component
class MeasuringAspect {

    @Around("@annotation(Measured)")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.nanoTime() - start;
            // log duration
        }
    }
}
```

Sprawdź:

- czy aspekt działa na metodzie publicznej,
- czy działa przy self-invocation,
- czy działa na metodzie prywatnej,
- czy działa na metodzie wywołanej z innego beana,
- jak zachowuje się przy klasach finalnych i metodach finalnych.

---

## SQL i wydajność bazy danych

### Główne tabele

Przykładowy model relacyjny:

```sql
organization(id, name, status, created_at)
app_user(id, organization_id, email, role, status, created_at)
event(id, organization_id, name, city, category, start_time, end_time, status, created_at)
capacity_pool(id, event_id, total_capacity, available_capacity, version, created_at, updated_at)
reservation(id, event_id, capacity_pool_id, customer_id, organization_id, status, expires_at, confirmed_at, cancelled_at, created_at, updated_at)
payment(id, reservation_id, provider_reference, status, amount, currency, created_at, updated_at)
audit_log(id, actor_id, organization_id, action, resource_type, resource_id, created_at, metadata)
```

### Zapytania do optymalizacji

#### Search events

```sql
SELECT *
FROM event
WHERE city = ?
  AND category = ?
  AND start_time >= ?
ORDER BY start_time ASC
LIMIT 20;
```

Indeks do przetestowania:

```sql
CREATE INDEX idx_event_city_category_start_time
ON event(city, category, start_time);
```

Do opisania w dokumentacji:

- czy indeks został użyty,
- jaka jest selektywność `city`,
- jaka jest selektywność `category`,
- czy kolejność kolumn w indeksie ma sens,
- czy sortowanie korzysta z indeksu.

#### Organization reservations

```sql
SELECT *
FROM reservation
WHERE organization_id = ?
  AND status = ?
ORDER BY created_at DESC
LIMIT 20;
```

Indeks do przetestowania:

```sql
CREATE INDEX idx_reservation_org_status_created_at
ON reservation(organization_id, status, created_at DESC);
```

#### Customer reservations

```sql
SELECT *
FROM reservation
WHERE customer_id = ?
ORDER BY created_at DESC
LIMIT 20;
```

Indeks:

```sql
CREATE INDEX idx_reservation_customer_created_at
ON reservation(customer_id, created_at DESC);
```

### EXPLAIN ANALYZE

Każde ważne zapytanie powinno mieć zapisany plan przed i po optymalizacji.

Struktura dokumentacji:

```text
docs/sql-explain/
  search-events-before.sql
  search-events-after.sql
  organization-reservations-before.sql
  organization-reservations-after.sql
  customer-reservations-before.sql
  customer-reservations-after.sql
```

Wnioski powinny odpowiadać na pytania:

- czy baza robi sequential scan,
- czy używa index scan,
- czy robi sort,
- czy estymacje liczby wierszy są bliskie rzeczywistości,
- czy problemem jest CPU, IO, sortowanie, join czy zły indeks,
- czy indeks pomaga odczytom, ale szkodzi zapisom,
- czy indeks jest potrzebny dla realnego workloadu.

### Offset pagination vs keyset pagination

Offset pagination:

```sql
SELECT *
FROM reservation
WHERE organization_id = ?
ORDER BY created_at DESC
LIMIT 20 OFFSET 100000;
```

Problem:

- baza musi przejść przez dużą liczbę pominiętych rekordów,
- latency rośnie wraz z offsetem,
- wyniki mogą być niestabilne przy nowych insertach.

Keyset pagination:

```sql
SELECT *
FROM reservation
WHERE organization_id = ?
  AND created_at < ?
ORDER BY created_at DESC
LIMIT 20;
```

Lepszy wariant z tie-breakerem:

```sql
SELECT *
FROM reservation
WHERE organization_id = ?
  AND (created_at, id) < (?, ?)
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

Wymagany indeks:

```sql
CREATE INDEX idx_reservation_org_created_id
ON reservation(organization_id, created_at DESC, id DESC);
```

W dokumentacji należy pokazać różnicę w czasie wykonania dla małego i dużego offsetu.

---

## JVM, profilowanie i benchmarki

### Scenariusze profilowania

Projekt powinien mieć kilka endpointów lub komend, które da się profilować.

Przykładowe scenariusze:

1. 1000 równoległych prób rezerwacji dla małej puli miejsc.
2. Generowanie raportu dla organizacji z dużą liczbą rezerwacji.
3. Endpoint z celowym N+1.
4. Endpoint z wysokim allocation rate.
5. Endpoint z celowym lock contention.
6. Endpoint CPU-bound z przetwarzaniem dużej kolekcji.
7. Endpoint IO-bound symulujący zewnętrzne wywołania.

### Metryki do zebrania

Dla każdego scenariusza warto zapisać:

- throughput,
- p50 latency,
- p95 latency,
- p99 latency,
- CPU usage,
- allocation rate,
- GC pauses,
- liczba wątków,
- lock contention,
- liczba zapytań SQL,
- czas najwolniejszych zapytań.

### JFR

Przykładowe uruchomienie:

```bash
java \
  -XX:StartFlightRecording=filename=recording.jfr,duration=120s,settings=profile \
  -jar build/libs/booking-capacity-platform.jar
```

Wnioski z JFR powinny trafić do:

```text
docs/performance-notes.md
```

Przykładowy format:

```markdown
## Scenario: Concurrent reservations

### Setup

- capacity: 100
- attempts: 10 000
- thread pool: 64
- database: PostgreSQL in Docker
- strategy: atomic SQL update

### Observations

- p95 latency: ...
- allocation rate: ...
- top CPU method: ...
- GC pauses: ...
- SQL bottleneck: ...

### Conclusion

The main bottleneck is not Java CPU usage but database row contention on `capacity_pool`.
```

### JMH

Benchmarki powinny być w osobnym module albo katalogu:

```text
benchmark/
  src/jmh/java/...
```

Wymagane benchmarki:

1. `ArrayList` vs `LinkedList`,
2. stream vs loop,
3. `BigDecimal` vs `long`,
4. `AtomicLong` vs `LongAdder`,
5. object pooling vs zwykła alokacja,
6. monomorphic vs megamorphic call site,
7. false sharing demo.

Przykładowe zasady:

- używać warm-up,
- nie mierzyć przez samo `System.nanoTime()` jako głównego benchmarku,
- uważać na dead code elimination,
- używać `Blackhole`,
- nie wyciągać ogólnych wniosków z mikrobenchmarku bez kontekstu.

---

## Asynchroniczność

Asynchroniczność powinna być użyta tam, gdzie ma sens biznesowy, a nie jako dekoracja.

### Przykłady async flow

Po utworzeniu rezerwacji:

- wysłanie maila,
- zapis audytu,
- aktualizacja read modelu,
- publikacja eventu wewnętrznego.

Po potwierdzeniu rezerwacji:

- potwierdzenie płatności,
- wysłanie biletu,
- zapis audytu,
- notyfikacja organizatora.

### ExecutorService

Należy skonfigurować własną pulę, zamiast bezrefleksyjnie używać common pool.

Przykład:

```java
@Bean(destroyMethod = "shutdown")
ExecutorService notificationExecutor() {
    return new ThreadPoolExecutor(
        4,
        16,
        60L,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(1000),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );
}
```

Do sprawdzenia:

- co się dzieje po zapełnieniu kolejki,
- czy aplikacja poprawnie zamyka pulę,
- czy taski reagują na przerwanie,
- czy timeouty są obsłużone,
- czy błędy z async flow nie znikają w logach.

### CompletableFuture

Przykładowy flow:

```java
CompletableFuture<PaymentResult> payment = paymentClient.confirmAsync(reservationId);
CompletableFuture<Void> audit = auditLogWriter.writeAsync(...);
CompletableFuture<Void> email = emailSender.sendAsync(...);

CompletableFuture.allOf(payment, audit, email)
    .orTimeout(2, TimeUnit.SECONDS)
    .exceptionally(ex -> {
        // fallback / compensation / logging
        return null;
    });
```

Do przećwiczenia:

- `thenApply`,
- `thenCompose`,
- `thenCombine`,
- `allOf`,
- `anyOf`,
- `orTimeout`,
- `completeOnTimeout`,
- `exceptionally`,
- `handle`,
- propagacja wyjątków,
- anulowanie.

### Cancellation

W projekcie powinien istnieć task, który można anulować.

Przykład:

- generowanie raportu,
- masowe wygaszanie rezerwacji,
- symulowana długa operacja payment providera.

Kod powinien poprawnie obsługiwać `InterruptedException`:

```java
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new TaskCancelledException("Task interrupted", e);
}
```

---

## Security i autoryzacja

Security w tym projekcie nie powinno kończyć się na rolach.

### Authentication

Minimalny wariant:

- JWT access token,
- refresh token,
- rotacja refresh tokenów,
- resource server,
- hashowanie haseł,
- rozróżnienie authentication i authorization.

### Authorization

Reguły:

- `CUSTOMER` widzi tylko swoje rezerwacje,
- `EVENT_MANAGER` widzi wydarzenia swojej organizacji,
- `ORG_ADMIN` zarządza użytkownikami w swojej organizacji,
- `HR` widzi dane pracowników swojej organizacji,
- `SUPPORT` może widzieć zgłoszenia, ale nie pełne dane płatności,
- nikt nie może przekroczyć tenant boundary.

Przykład:

```java
@PreAuthorize("@reservationSecurity.canView(authentication, #reservationId)")
public ReservationDto getReservation(UUID reservationId) {
    return getReservationUseCase.execute(reservationId);
}
```

### Data-based authorization

Nie wystarczy:

```java
@PreAuthorize("hasRole('ADMIN')")
```

Lepszy kierunek:

```java
boolean canView(Authentication authentication, UUID reservationId) {
    UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
    ReservationAccessView reservation = reservationAccessRepository.getAccessView(reservationId);

    if (principal.hasRole("CUSTOMER")) {
        return reservation.customerId().equals(principal.userId());
    }

    if (principal.hasRole("EVENT_MANAGER")) {
        return reservation.organizationId().equals(principal.organizationId());
    }

    return false;
}
```

Ryzyka do opisania:

- koszt dodatkowych zapytań autoryzacyjnych,
- cache uprawnień,
- stale permissions,
- tenant leakage,
- testowanie negatywnych scenariuszy dostępu.

---

## NoSQL i cache

NoSQL powinien zostać użyty jako świadoma decyzja projektowa.

### Redis

Zastosowania:

- cache szczegółów wydarzenia,
- cache snapshotu dostępności,
- TTL dla tymczasowych rezerwacji,
- rate limiting,
- deduplikacja idempotency key.

Ważne:

- Redis nie powinien być źródłem prawdy dla krytycznej dostępności miejsc,
- cache może być nieaktualny,
- UI musi być odporne na eventual consistency,
- zapis rezerwacji powinien być poprawny nawet wtedy, gdy cache pokazuje starą dostępność.

### MongoDB jako read model

Dokument:

```json
{
  "eventId": "1a1b2c3d-0000-4000-9000-111111111111",
  "organizationId": "7b7b7d7c-0f7e-4b42-8c68-8e75d95c1111",
  "name": "Java Backend Performance Workshop",
  "city": "Warsaw",
  "category": "workshop",
  "startTime": "2026-06-01T10:00:00Z",
  "availableCapacitySnapshot": 42,
  "organizationName": "Backend Academy",
  "tags": ["java", "spring", "performance"]
}
```

Zastosowanie:

- szybkie wyszukiwanie i odczyt danych do UI,
- denormalizowany widok,
- oddzielenie write modelu od read modelu.

Ryzyka:

- opóźnienie synchronizacji,
- problem read-your-writes,
- konieczność odbudowy read modelu,
- niespójność między PostgreSQL i MongoDB.

---

## Testy

Testy są jednym z głównych celów projektu. Nie chodzi o samo pokrycie procentowe, ale o zdolność wykrywania realnych błędów.

### Unit tests

Zakres:

- reguły domenowe,
- przejścia statusów,
- walidacje biznesowe,
- polityki rezerwacji,
- polityki autoryzacji,
- kalkulacje.

Zasady:

- bez Spring Context,
- szybkie,
- deterministyczne,
- bez bazy danych,
- bez mockowania wszystkiego.

### Integration tests

Zakres:

- repozytoria,
- transakcje,
- locking,
- migracje,
- realne zapytania SQL,
- Testcontainers z PostgreSQL.

### MVC tests

Zakres:

- kontrakt REST API,
- walidacja requestów,
- mapowanie błędów,
- `@ControllerAdvice`,
- security na poziomie endpointów.

Narzędzie:

- `@WebMvcTest`.

### JPA tests

Zakres:

- mapping encji,
- relacje,
- queries,
- entity graph,
- fetch join,
- N+1.

Narzędzie:

- `@DataJpaTest`,
- Testcontainers, jeśli potrzebne PostgreSQL-specific behavior.

### Spring Boot integration tests

Zakres:

- pełny flow biznesowy,
- konfiguracja aplikacji,
- integracja warstw.

Narzędzie:

- `@SpringBootTest`.

Uwaga:

- nie wszystko powinno być testowane przez `@SpringBootTest`, bo testy staną się wolne i trudniejsze w diagnozowaniu.

### Concurrency tests

Zakres:

- overselling,
- lost update,
- optimistic locking conflict,
- pessimistic locking,
- deadlock,
- timeout,
- cancellation.

Zasady:

- nie opierać testów wyłącznie o `Thread.sleep`,
- używać latchy i barier,
- wymuszać równoczesny start wątków,
- wykonywać test wielokrotnie, jeśli błąd jest niedeterministyczny,
- oddzielić testy edukacyjne od stabilnego CI, jeśli są flaky z natury.

### Regression tests

Każdy znaleziony bug powinien dostać test regresyjny.

Przykład:

```text
Bug: Reservation could be confirmed after expiration.
Regression test: shouldRejectConfirmationOfExpiredReservation.
```

### Negative tests

Przykłady:

- customer nie może zobaczyć cudzej rezerwacji,
- manager nie może zobaczyć wydarzenia innej organizacji,
- nie można potwierdzić anulowanej rezerwacji,
- nie można anulować rezerwacji dwa razy i podwójnie zwolnić miejsca,
- nie można utworzyć wydarzenia z ujemną pojemnością,
- nie można przekroczyć tenant boundary.

---

## Struktura repozytorium

Rekomendowana struktura:

```text
booking-capacity-platform/
  README.md
  build.gradle.kts
  settings.gradle.kts
  docker-compose.yml

  src/main/java/pl/jakubtworek/booking/
    BookingCapacityApplication.java

    domain/
      event/
      reservation/
      capacity/
      payment/
      organization/
      user/
      audit/

    application/
      event/
      reservation/
      payment/
      organization/
      security/
      audit/

    infrastructure/
      persistence/
        jpa/
        mapper/
        repository/
      payment/
      mail/
      cache/
      nosql/
      audit/

    adapters/
      web/
        event/
        reservation/
        organization/
      scheduler/
      security/

    config/
      AsyncConfig.java
      SecurityConfig.java
      PersistenceConfig.java

  src/main/resources/
    application.yml
    application-local.yml
    db/migration/

  src/test/java/pl/jakubtworek/booking/
    unit/
    integration/
    concurrency/
    mvc/
    jpa/
    architecture/

  benchmark/
    build.gradle.kts
    src/jmh/java/pl/jakubtworek/booking/benchmark/

  docs/
    decisions.md
    performance-notes.md
    concurrency-notes.md
    spring-pitfalls.md
    sql-explain/
    jfr/
    jmh-results/
```

---

## Lokalne uruchomienie

### Wymagania

- Java 21 lub nowsza,
- Gradle,
- Docker,
- Docker Compose,
- PostgreSQL przez kontener,
- opcjonalnie Redis,
- opcjonalnie MongoDB.

### Uruchomienie infrastruktury

```bash
docker compose up -d
```

Przykładowy `docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:16
    container_name: booking-postgres
    environment:
      POSTGRES_DB: booking
      POSTGRES_USER: booking
      POSTGRES_PASSWORD: booking
    ports:
      - "5432:5432"
    volumes:
      - booking-postgres-data:/var/lib/postgresql/data

  redis:
    image: redis:7
    container_name: booking-redis
    ports:
      - "6379:6379"

  mongo:
    image: mongo:7
    container_name: booking-mongo
    ports:
      - "27017:27017"

volumes:
  booking-postgres-data:
```

### Uruchomienie aplikacji

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Uruchomienie testów

```bash
./gradlew test
```

### Uruchomienie testów integracyjnych

Jeśli testy są rozdzielone taskami:

```bash
./gradlew integrationTest
```

### Uruchomienie benchmarków JMH

```bash
./gradlew :benchmark:jmh
```

---

## Migracje bazy danych

Rekomendowane narzędzie:

- Flyway.

Przykładowa struktura:

```text
src/main/resources/db/migration/
  V1__create_organization.sql
  V2__create_user.sql
  V3__create_event.sql
  V4__create_capacity_pool.sql
  V5__create_reservation.sql
  V6__create_payment.sql
  V7__create_audit_log.sql
  V8__add_indexes.sql
```

Zasady:

- migracje muszą być deterministyczne,
- indeksy dodawać świadomie,
- duże migracje danych dokumentować,
- nie poprawiać starych migracji po ich użyciu; dodawać nowe,
- testy integracyjne powinny uruchamiać migracje na czystej bazie.

---

## Dane testowe

Do testów performance potrzebny jest większy dataset.

Docelowo:

- 100 000 wydarzeń,
- 1 000 000 rezerwacji,
- kilka tysięcy użytkowników,
- kilkaset organizacji,
- różne statusy rezerwacji,
- różne miasta,
- różne kategorie,
- różne daty.

Przykładowy generator:

```text
TestDataGenerator
- createOrganizations(count)
- createUsersPerOrganization(count)
- createEvents(count)
- createReservations(count)
- createPayments(count)
```

Dane powinny być realistycznie rozłożone:

- kilka popularnych miast powinno mieć dużo wydarzeń,
- większość wydarzeń powinna mieć mało rezerwacji,
- kilka wydarzeń powinno być bardzo popularnych,
- statusy powinny mieć nierówny rozkład,
- daty powinny obejmować przeszłość i przyszłość.

To jest ważne, bo indeksy i plany zapytań zależą od rozkładu danych.

---

## Dokumentacja decyzji technicznych

Projekt powinien zawierać plik:

```text
docs/decisions.md
```

Każda większa decyzja powinna mieć format ADR-lite.

Przykład:

```markdown
# Decision 001: Use atomic SQL update for capacity reservation

## Context

Multiple users can reserve capacity for the same event concurrently. A naive read-modify-write implementation can cause overselling.

## Options

1. synchronized block in Java
2. optimistic locking
3. pessimistic locking
4. atomic SQL update

## Decision

Use atomic SQL update for the main reservation path.

## Consequences

Positive:
- short database operation
- no application-level check-then-act
- good fit for decrementing capacity

Negative:
- some business logic is expressed in SQL
- requires careful transaction boundary with reservation insert

## Validation

The decision is validated by concurrency tests and load tests.
```

Minimalne decyzje do opisania:

- strategia ochrony przed oversellingiem,
- granice transakcji w rezerwacji,
- optimistic vs pessimistic locking,
- offset vs keyset pagination,
- strategia cache,
- wybór read modelu,
- strategia testów,
- struktura pakietów,
- strategia autoryzacji.

---

## Plan realizacji

### Miesiąc 1 — MVP i fundamenty

Cele:

- REST API,
- PostgreSQL,
- Flyway,
- podstawowy model domenowy,
- tworzenie wydarzeń,
- tworzenie rezerwacji,
- potwierdzanie i anulowanie rezerwacji,
- globalny exception handling,
- pierwsze testy unit i integration.

Efekt końcowy:

- system działa lokalnie,
- da się przejść podstawowy flow,
- API ma spójny error contract,
- domena nie jest przyklejona do kontrolerów.

### Miesiąc 2 — Concurrency i transakcje

Cele:

- naiwny wariant rezerwacji,
- test pokazujący overselling,
- optimistic locking,
- pessimistic locking,
- atomic SQL update,
- analiza trade-offów,
- deadlock demo,
- timeouty locków.

Efekt końcowy:

- masz test, który łamie naiwną implementację,
- masz kilka napraw,
- potrafisz wyjaśnić, którą strategię wybierasz i dlaczego.

### Miesiąc 3 — Spring internals i JPA

Cele:

- self-invocation demo,
- `@Transactional` pitfalls,
- lazy loading demo,
- N+1 demo,
- flush vs commit,
- dirty checking,
- AOP measurement aspect,
- testy Spring slice.

Efekt końcowy:

- osobna dokumentacja `docs/spring-pitfalls.md`,
- każdy błąd ma test reprodukujący problem,
- każda naprawa ma opis trade-offów.

### Miesiąc 4 — SQL performance i profilowanie

Cele:

- duży dataset,
- `EXPLAIN ANALYZE`,
- indeksy,
- composite indexes,
- offset vs keyset pagination,
- JFR,
- profiler,
- analiza allocation rate,
- analiza lock contention.

Efekt końcowy:

- `docs/performance-notes.md`,
- plany zapytań przed i po,
- wnioski z JFR,
- konkretne poprawki performance.

### Miesiąc 5 — Async i resilience

Cele:

- własne pule wątków,
- `CompletableFuture`,
- fan-out/fan-in,
- timeouty,
- fallbacki,
- cancellation,
- scheduled expiration,
- poprawny shutdown executorów,
- symulowany payment provider.

Efekt końcowy:

- async flow działa,
- błędy nie znikają po cichu,
- timeouty są kontrolowane,
- puli wątków nie da się łatwo zalać bez konsekwencji.

### Miesiąc 6 — Security, refaktoryzacja i finalizacja

Cele:

- JWT,
- refresh token rotation,
- data-based authorization,
- tenant boundary,
- Redis cache,
- opcjonalny MongoDB read model,
- refaktor dużych serwisów,
- testy regresyjne,
- dokumentacja decyzji.

Efekt końcowy:

- projekt nadaje się jako portfolio,
- README i docs opisują nie tylko “co”, ale też “dlaczego”,
- kod pokazuje dojrzałe decyzje backendowe.

---

## Definition of Done

Projekt można uznać za ukończony, jeśli spełnia poniższe warunki.

### Concurrency

- [ ] Istnieje test reprodukujący race condition w naiwnej implementacji.
- [ ] Istnieje test wykrywający overselling.
- [ ] Istnieją co najmniej trzy strategie naprawy oversellingu.
- [ ] Wybrana strategia produkcyjna jest opisana w `docs/decisions.md`.
- [ ] Istnieje przykład deadlocka i jego naprawy.
- [ ] Kod poprawnie obsługuje `InterruptedException`.
- [ ] ExecutorService jest poprawnie zamykany.

### Spring

- [ ] Istnieje przykład self-invocation, gdzie `@Transactional` nie działa zgodnie z intuicją.
- [ ] Istnieje naprawa self-invocation.
- [ ] Istnieje przykład `LazyInitializationException`.
- [ ] Istnieje przykład N+1.
- [ ] Istnieją co najmniej dwie strategie naprawy N+1.
- [ ] Istnieje własny aspekt AOP i test pokazujący proxy boundary.

### SQL

- [ ] Istnieją plany `EXPLAIN ANALYZE` przed i po dodaniu indeksów.
- [ ] Indeksy są uzasadnione access patternem.
- [ ] Istnieje porównanie offset pagination i keyset pagination.
- [ ] Istnieje przykład optimistic locking.
- [ ] Istnieje przykład pessimistic locking.
- [ ] Istnieje przykład atomowego update'u SQL.

### JVM i performance

- [ ] Istnieje raport z JFR albo profilera.
- [ ] W raporcie wskazano konkretny bottleneck.
- [ ] Istnieją benchmarki JMH.
- [ ] Benchmarki nie opierają się na naiwnym `System.nanoTime()`.
- [ ] Istnieje analiza allocation rate.
- [ ] Istnieje analiza lock contention albo thread count.

### Security

- [ ] Istnieje JWT authentication.
- [ ] Istnieje refresh token rotation.
- [ ] Autoryzacja nie opiera się wyłącznie na rolach.
- [ ] Istnieje data-based authorization.
- [ ] Tenant boundary jest testowany negatywnie.
- [ ] Użytkownik z jednej organizacji nie widzi danych innej organizacji.

### Testy

- [ ] Istnieją unit testy domeny bez Springa.
- [ ] Istnieją testy repozytoriów.
- [ ] Istnieją testy MVC.
- [ ] Istnieją testy security.
- [ ] Istnieją testy concurrency.
- [ ] Istnieją testy regresyjne.
- [ ] Istnieją testy negatywne.

### Clean Code

- [ ] Domena nie zależy od Springa.
- [ ] Encje JPA nie są zwracane bezpośrednio z API.
- [ ] Duży serwis został zrefaktoryzowany bez zmiany zachowania.
- [ ] Granice odpowiedzialności są widoczne w strukturze pakietów.
- [ ] Decyzje techniczne są opisane w dokumentacji.

---

## Antywzorce, które projekt ma ujawnić

Ten projekt powinien świadomie pokazać i naprawić poniższe błędy.

### Naiwne check-then-act

```java
if (available > 0) {
    available--;
}
```

Problem:

- nie jest atomowe,
- prowadzi do lost update,
- może powodować overselling.

### Nadużywanie `@SpringBootTest`

Problem:

- wolne testy,
- trudna diagnostyka,
- testowanie wszystkiego przez pełny kontekst.

Lepsze podejście:

- unit test dla domeny,
- `@WebMvcTest` dla kontrolera,
- `@DataJpaTest` dla repozytorium,
- `@SpringBootTest` tylko dla pełnych flow.

### Encje JPA jako DTO

Problem:

- wyciekanie modelu persistence do API,
- lazy loading podczas serializacji,
- przypadkowe N+1,
- trudna ewolucja API.

Lepsze podejście:

- osobne DTO,
- projection queries,
- mapowanie w adapterze.

### `@Transactional` bez zrozumienia proxy

Problem:

- self-invocation,
- prywatne metody,
- final methods/classes,
- błędne oczekiwania wobec rollbacku.

Lepsze podejście:

- świadome granice przypadków użycia,
- metody transakcyjne wywoływane przez proxy,
- testy potwierdzające zachowanie.

### Async bez kontroli

Problem:

- common pool użyty przypadkowo,
- brak timeoutów,
- brak obsługi wyjątków,
- brak shutdown,
- nieograniczone kolejki.

Lepsze podejście:

- jawny `ThreadPoolExecutor`,
- bounded queue,
- rejection policy,
- timeouty,
- monitoring,
- poprawna obsługa błędów.

### Indeksy “na wszelki wypadek”

Problem:

- indeksy spowalniają zapisy,
- zajmują miejsce,
- mogą nie być używane,
- nie rozwiązują złego access patternu.

Lepsze podejście:

- indeks wynika z zapytania,
- potwierdzenie przez `EXPLAIN ANALYZE`,
- pomiar przed i po.

### Cache jako źródło prawdy

Problem:

- cache może być nieaktualny,
- race condition nadal istnieje,
- użytkownik może widzieć błędną dostępność.

Lepsze podejście:

- PostgreSQL jako source of truth,
- Redis jako optymalizacja odczytu,
- API odporne na eventual consistency.

---

## Rozszerzenia opcjonalne

Po ukończeniu podstawowego zakresu można dodać kolejne moduły.

### Idempotency key

Problem:

- klient wysyła ten sam request kilka razy,
- sieć przerywa odpowiedź,
- retry może utworzyć podwójną rezerwację.

Rozwiązanie:

- endpoint `POST /reservations` przyjmuje `Idempotency-Key`,
- system zapisuje wynik pierwszego requestu,
- kolejne requesty z tym samym kluczem zwracają ten sam wynik.

### Outbox pattern

Problem:

- zapis rezerwacji się udał,
- publikacja eventu się nie udała,
- system jest częściowo niespójny.

Rozwiązanie:

- zapis eventu do tabeli `outbox_event` w tej samej transakcji,
- osobny worker publikuje event,
- retry i statusy publikacji.

### Rate limiting

Problem:

- jeden użytkownik zalewa endpoint rezerwacji,
- system zużywa zasoby na requesty, które powinny być ograniczone.

Rozwiązanie:

- Redis counter z TTL,
- limit per user/IP/organization,
- odpowiedź `429 Too Many Requests`.

### Load testing

Narzędzia:

- k6,
- Gatling,
- JMeter.

Scenariusze:

- burst rezerwacji na jedno wydarzenie,
- read-heavy search events,
- organization reservations pagination,
- payment provider timeout.

### Observability

Dodać:

- structured logging,
- correlation ID,
- Micrometer metrics,
- Prometheus,
- Grafana,
- slow query logging,
- business metrics.

Przykładowe metryki biznesowe:

```text
reservations.created
reservations.confirmed
reservations.cancelled
reservations.expired
capacity.exhausted
payment.timeout
authorization.denied
```

---

## Główna zasada projektu

Nie chodzi o to, żeby dopisać technologię do checklisty.

Każdy element powinien mieć powód:

- concurrency wynika z ograniczonej dostępności,
- transakcje wynikają z potrzeby spójności,
- indeksy wynikają z realnych zapytań,
- cache wynika z kosztownych odczytów,
- async wynika z operacji pobocznych,
- security wynika z tenant boundary,
- testy wynikają z ryzyka regresji,
- profilowanie wynika z konkretnego pytania: “dlaczego to jest wolne?”.

Najlepszy efekt końcowy to nie tylko działający kod, ale repozytorium, w którym widać dojrzałość techniczną: problem, pomiar, decyzję, trade-off i test potwierdzający zachowanie.
