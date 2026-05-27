# Blokady optymistyczne i pesymistyczne — szczegółowe podsumowanie

# Wprowadzenie

Jednym z najważniejszych problemów w systemach współbieżnych jest kontrola równoczesnych modyfikacji danych. Gdy wiele transakcji próbuje jednocześnie zmieniać te same rekordy, bardzo łatwo doprowadzić do:
- utraty danych,
- niespójności,
- race conditions,
- złamania invariants,
- lost updates,
- błędów biznesowych.

Dlatego systemy bazodanowe implementują mechanizmy kontroli współbieżności.

Dwa najważniejsze podejścia to:
- pessimistic locking,
- optimistic locking.

Oba rozwiązują ten sam problem, ale robią to w zupełnie inny sposób i mają kompletnie inne właściwości wydajnościowe.

---

# Pessimistic Locking — blokowanie z góry

Pessimistic locking zakłada:

> konflikt jest prawdopodobny,
> więc należy zablokować dane natychmiast.

Najczęściej realizowane jest przez:

```sql
SELECT ... FOR UPDATE
```

---

# Jak działa FOR UPDATE

Przykład:

```sql
BEGIN;

SELECT *
FROM accounts
WHERE id = 1
FOR UPDATE;

UPDATE accounts
SET balance = balance - 100
WHERE id = 1;

COMMIT;
```

W momencie wykonania:

```sql
FOR UPDATE
```

PostgreSQL:
- zakłada lock na rekord,
- uniemożliwia innym transakcjom jego modyfikację,
- utrzymuje blokadę aż do COMMIT lub ROLLBACK.

---

# Co dzieje się w innych transakcjach

Jeżeli druga transakcja spróbuje:

```sql
UPDATE accounts
SET balance = balance - 200
WHERE id = 1;
```

to:
- będzie czekać,
- zostanie zablokowana,
- wykona się dopiero po zwolnieniu locka.

To bardzo ważna właściwość:
- pessimistic locking eliminuje konflikty przez serializację dostępu.

---

# Zalety pessimistic locking

Największą zaletą jest:
- prostota modelu mentalnego.

Programista może zakładać:
- „mam locka, więc nikt nie zmieni danych”.

To bardzo wygodne dla:
- systemów finansowych,
- inventory management,
- systemów rezerwacji,
- payment processing,
- krytycznych invariants.

Pessimistic locking:
- dobrze chroni consistency,
- minimalizuje race conditions,
- eliminuje wiele klas błędów.

---

# Wady pessimistic locking

Problemem jest concurrency.

Blokady powodują:
- oczekiwanie,
- contention,
- wzrost latency,
- spadek throughputu.

Jeżeli:
- wiele transakcji chce modyfikować te same rekordy,
- workload jest write-heavy,
- transakcje są długie,

system zaczyna:
- kolejkować operacje,
- generować lock waits,
- ograniczać równoległość.

---

# Lock Contention

Jednym z najważniejszych problemów pessimistic locking jest:

```text
lock contention
```

Im więcej:
- współbieżnych writerów,
- locków,
- długich transakcji,

tym bardziej throughput zaczyna spadać.

W skrajnych przypadkach:
- CPU się nudzi,
- ale requesty czekają na locki.

To bardzo częsty problem dużych systemów OLTP.

---

# Deadlock

Pessimistic locking wprowadza również ryzyko:

```text
deadlock
```

Deadlock oznacza:
- transakcja A czeka na lock trzymany przez B,
- transakcja B czeka na lock trzymany przez A.

Przykład:
- T1 lockuje konto 1,
- T2 lockuje konto 2,
- T1 próbuje zablokować konto 2,
- T2 próbuje zablokować konto 1.

Powstaje cykl oczekiwania.

PostgreSQL:
- wykrywa deadlock,
- przerywa jedną transakcję,
- zwraca błąd.

---

# Jak ograniczać deadlocki

Najczęstsza strategia:
- zawsze lockować rekordy w tej samej kolejności.

Przykład:
```text
zawsze rosnąco po account_id
```

To bardzo ważna praktyka produkcyjna.

---

# NOWAIT i SKIP LOCKED

PostgreSQL oferuje dodatkowe mechanizmy:
- NOWAIT,
- SKIP LOCKED.

---

# FOR UPDATE NOWAIT

```sql
SELECT *
FROM accounts
WHERE id = 1
FOR UPDATE NOWAIT;
```

Zamiast:
- czekać na lock,

transakcja:
- natychmiast zwraca błąd.

Przydaje się gdy:
- lepiej szybko failować,
- niż trzymać request w oczekiwaniu.

---

# FOR UPDATE SKIP LOCKED

```sql
FOR UPDATE SKIP LOCKED
```

powoduje:
- pomijanie zablokowanych rekordów.

To bardzo popularne w:
- worker queues,
- job processing,
- distributed schedulers.

Dzięki temu:
- wiele workerów może równolegle pobierać zadania,
- bez wzajemnego blokowania.

---

# Optimistic Locking — zakładamy brak konfliktu

Optimistic locking działa odwrotnie.

Założenie brzmi:

> konflikty są rzadkie,
> więc nie warto blokować danych z góry.

Zamiast locków stosuje się:
- detekcję konfliktu podczas zapisu.

---

# Mechanizm version column

Najczęściej używa się:

```sql
version INT
```

Każdy rekord posiada numer wersji.

Przykład:

```sql
UPDATE accounts
SET
    balance = balance - 100,
    version = version + 1
WHERE
    id = 1
    AND version = 7;
```

---

# Jak działa optimistic locking

Aplikacja:
1. odczytuje rekord,
2. zapamiętuje version,
3. wykonuje logikę biznesową,
4. próbuje wykonać UPDATE z warunkiem version.

Jeżeli:
- version nadal się zgadza,
- UPDATE powiedzie się.

Jeżeli:
- ktoś wcześniej zmienił rekord,
- version jest już inne,
- UPDATE zmodyfikuje 0 rekordów.

To oznacza konflikt.

---

# Conflict Detection

Kluczowa różnica:

Pessimistic locking:
- zapobiega konfliktom wcześniej.

Optimistic locking:
- wykrywa konflikty dopiero przy zapisie.

---

# Retry Logic

W optimistic locking aplikacja musi obsługiwać retry.

Workflow wygląda zwykle tak:
1. read,
2. calculate,
3. update with version,
4. if rows_affected = 0:
    - reload,
    - retry transaction.

To fundamentalna część optimistic concurrency control.

---

# Dlaczego optimistic locking dobrze się skaluje

Największą zaletą optimistic locking jest brak blokowania.

To oznacza:
- brak lock waits,
- brak oczekiwania,
- wysoki concurrency,
- bardzo dobry throughput.

Transakcje:
- nie blokują się wzajemnie podczas odczytu,
- mogą działać równolegle.

---

# Kiedy optimistic locking działa najlepiej

Najlepiej sprawdza się gdy:
- konflikty są rzadkie,
- workload jest read-heavy,
- wielu użytkowników czyta dane,
- niewiele transakcji zapisuje jednocześnie ten sam rekord.

To bardzo częsty przypadek dla:
- większości aplikacji webowych,
- SaaS,
- dashboardów,
- CRUD systems.

---

# Wady optimistic locking

Problem pojawia się przy:
- wysokim contention,
- wielu writerach,
- gorących rekordach.

Wtedy:
- liczba konfliktów rośnie,
- retry stają się częste,
- dużo pracy jest marnowane,
- throughput zaczyna spadać.

W skrajnych przypadkach może pojawić się:
```text
retry storm
```

czyli sytuacja, w której:
- ogromna liczba transakcji stale retry’uje,
- system zaczyna tracić wydajność.

---

# Pessimistic vs Optimistic — różnica filozofii

Pessimistic locking:
```text
zakładamy konflikt
i blokujemy wcześniej
```

Optimistic locking:
```text
zakładamy brak konfliktu
i wykrywamy problem później
```

---

# Kiedy używać pessimistic locking

Najlepiej sprawdza się dla:
- finansów,
- inventory,
- booking systems,
- payment systems,
- bardzo silnych invariants,
- wysokiego contention.

Szczególnie gdy:
- retry są kosztowne,
- correctness jest absolutnym priorytetem.

---

# Kiedy używać optimistic locking

Najlepiej sprawdza się dla:
- systemów webowych,
- read-heavy workload,
- dużego concurrency,
- rzadkich konfliktów,
- wysokiej skalowalności.

Bardzo często:
- optimistic locking daje większy throughput,
- niż blokowanie pesymistyczne.

---

# Throughput vs Correctness

To fundamentalny trade-off.

Pessimistic locking:
- zmniejsza concurrency,
- zwiększa correctness.

Optimistic locking:
- zwiększa concurrency,
- ale wymaga retry i obsługi konfliktów.

Nie istnieje uniwersalnie lepsze rozwiązanie.

Wybór zależy od:
- workloadu,
- częstotliwości konfliktów,
- wymagań consistency,
- charakteru biznesowego systemu.

---

# Najważniejsza praktyczna zasada

Największym błędem projektowym jest:
- ignorowanie współbieżności,
- zakładanie że requesty wykonują się sekwencyjnie.

W rzeczywistych systemach:
- wiele transakcji działa równolegle,
- requesty przeplatają się,
- konflikty są nieuniknione.

Dlatego:
- locking,
- retry logic,
- isolation levels,
- transaction design,
- contention management

są fundamentalną częścią projektowania backendów i systemów bazodanowych.