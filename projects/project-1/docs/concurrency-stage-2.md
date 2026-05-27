# Etap implementacji 2 — Concurrency

## Cel

Ten etap celowo pokazuje, że poprawnie działający kod sekwencyjny może być błędny przy równoległych requestach.

Scenariusz testowy:

- pula miejsc: `10`,
- liczba równoległych prób rezerwacji: `100`,
- oczekiwany wynik dla poprawnej implementacji: dokładnie `10` rezerwacji i `0` dostępnych miejsc,
- oczekiwany wynik dla implementacji naiwnej: więcej niż `10` zapisanych rezerwacji, czyli overselling.

Testy znajdują się w:

```text
src/test/java/pl/jakubtworek/booking/integration/ConcurrencyStage2IntegrationTest.java
```

## Problem: check-then-act

Naiwna implementacja wygląda logicznie poprawnie tylko wtedy, gdy patrzymy na jeden request:

```java
if (pool.getAvailableCapacity() > 0) {
    pool.setAvailableCapacity(pool.getAvailableCapacity() - 1);
    reservationRepository.save(...);
}
```

Przy wielu wątkach problemem jest rozdzielenie operacji:

1. odczytaj `availableCapacity`,
2. sprawdź `availableCapacity > 0`,
3. policz nową wartość,
4. zapisz rezerwację,
5. zapisz nową dostępność.

To jest klasyczny układ `check-then-act` oraz `read-modify-write`.
Kilka transakcji może przeczytać tę samą wartość i wszystkie uznać, że miejsce nadal jest dostępne.

## Implementacje dodane w tym etapie

### 1. `NaiveReservationService`

Celowo błędna implementacja.

Cechy:

- czyta `availableCapacity`,
- sprawdza warunek w Javie,
- robi sztuczne `Thread.sleep`, żeby zwiększyć okno wyścigu,
- wykonuje ślepy update dostępności,
- zapisuje rezerwację.

Ta implementacja jest po to, żeby test pokazał overselling. Nie należy jej używać jako produkcyjnego rozwiązania.

### 2. `SynchronizedReservationService`

Implementacja chroniona przez `synchronized`.

Zalety:

- prosta,
- działa w jednej instancji JVM,
- dobrze pokazuje, że serializacja sekcji krytycznej usuwa race condition.

Wady:

- nie działa jako ochrona między kilkoma instancjami aplikacji,
- zmniejsza przepustowość,
- miesza problem aplikacyjny z mechanizmem blokowania w pamięci procesu.

Wniosek: dobre ćwiczenie edukacyjne, słabe rozwiązanie produkcyjne dla systemu webowego skalowanego horyzontalnie.

### 3. `OptimisticLockingReservationService`

Implementacja używa pola `@Version` w encji `CapacityPool`.

Mechanizm:

- transakcja czyta wiersz puli,
- zmniejsza dostępność w obiekcie JPA,
- przy `flush/commit` Hibernate sprawdza wersję,
- jeżeli inna transakcja zmieniła wiersz wcześniej, dostajemy konflikt optimistic locking,
- serwis wykonuje retry.

Zalety:

- dobre przy niskim lub średnim konflikcie,
- brak długiego trzymania locka,
- naturalne dla JPA.

Wady:

- przy wysokim konflikcie może być dużo retry,
- kod musi świadomie obsłużyć konflikt,
- użytkownik może dostać błąd mimo tego, że miejsce było dostępne chwilę wcześniej.

### 4. `PessimisticLockingReservationService`

Implementacja używa blokady pesymistycznej:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

To odpowiada idei `SELECT ... FOR UPDATE`.

Zalety:

- bardzo czytelny model przy silnym konflikcie,
- tylko jedna transakcja naraz modyfikuje dany wiersz puli,
- dobrze chroni krytyczny zasób.

Wady:

- może zwiększać latency,
- może prowadzić do lock wait i deadlocków w bardziej złożonych flow,
- wymaga rozumienia transakcji i kolejności blokad.

### 5. `AtomicSqlReservationService`

Preferowana strategia w tym projekcie.

Kluczowy fragment:

```sql
UPDATE capacity_pools
SET available_capacity = available_capacity - 1
WHERE event_id = ?
  AND available_capacity > 0;
```

W kodzie jest to metoda:

```java
capacityPoolRepository.reserveOneSeatIfAvailable(eventId)
```

Zalety:

- warunek i modyfikacja są jedną operacją,
- nie ma rozdzielenia check i act,
- bardzo dobra strategia dla prostego licznika dostępności,
- mało kodu aplikacyjnego,
- dobra wydajność.

Wady:

- logika częściowo trafia do SQL,
- przy bardziej złożonych regułach może być mniej czytelna,
- trzeba uważać na to, żeby zapis rezerwacji i update dostępności były w jednej transakcji.

## Testy

Uruchom:

```bash
mvn test -Dtest=ConcurrencyStage2IntegrationTest
```

Cały zestaw:

```bash
mvn test
```

Testy używają:

- `ExecutorService`,
- `CountDownLatch`,
- równoczesnego startu wielu zadań,
- osobnych requestów z unikalnymi emailami klientów,
- asercji na liczbie zapisanych rezerwacji i stanie puli.

## Jak interpretować wyniki

### Naiwna implementacja

Test powinien pokazać więcej rezerwacji niż dostępnych miejsc.

To nie jest flaky test, który przypadkiem się wywraca. On przechodzi wtedy, kiedy uda się zreprodukować błąd. To świadomy test edukacyjny.

### Poprawione implementacje

Dla strategii:

- `synchronized`,
- optimistic locking,
- pessimistic locking,
- atomic SQL,

oczekujemy:

- `10` sukcesów,
- `90` porażek biznesowych lub konfliktów,
- `10` rezerwacji w bazie,
- `availableCapacity = 0`.

## Isolation levels

Domyślny `READ_COMMITTED` zwykle nie wystarcza, jeśli logika robi:

```text
SELECT available_capacity
if available_capacity > 0
UPDATE available_capacity
INSERT reservation
```

Problemem nie jest sam isolation level, tylko rozdzielenie odczytu, decyzji i zapisu.

Możliwe rozwiązania:

- przenieść warunek do atomowego `UPDATE`,
- użyć optimistic locking,
- użyć pessimistic locking,
- podnieść isolation level, ale to zwykle jest cięższe i mniej precyzyjne narzędzie.

W tym projekcie preferujemy atomowy update SQL, bo najlepiej pasuje do problemu prostego licznika miejsc.

## Wniosek końcowy

Sekwencyjny test MVP nie wystarcza. Kod rezerwacji zasobu ograniczonego musi być testowany równolegle.

Najważniejsza lekcja tego etapu:

> Jeżeli warunek biznesowy zależy od współdzielonego stanu, to sprawdzenie warunku i zmiana stanu muszą być jedną spójną operacją albo muszą być chronione mechanizmem kontroli współbieżności.
