# Transakcje i poziomy izolacji w PostgreSQL — szczegółowe podsumowanie

# Wprowadzenie

Transakcje są jednym z najważniejszych mechanizmów relacyjnych baz danych. To właśnie one odpowiadają za:
- spójność danych,
- atomowość operacji,
- izolację współbieżnych zmian,
- odporność na częściowe błędy.

Bez transakcji praktycznie niemożliwe byłoby poprawne działanie:
- systemów bankowych,
- płatności,
- systemów rezerwacji,
- inventory management,
- księgowości,
- aplikacji wieloużytkownikowych.

Problem pojawia się wtedy, gdy wiele transakcji działa równocześnie na tych samych danych. Współbieżność zwiększa wydajność systemu, ale jednocześnie prowadzi do ryzyka występowania anomalii.

Poziomy izolacji definiują:
- jakie dane transakcja może zobaczyć,
- jak wygląda snapshot danych,
- jakie anomalie są dopuszczalne,
- jak bardzo baza izoluje równoległe operacje.

---

# ACID i izolacja

Relacyjne bazy danych implementują model ACID:
- Atomicity,
- Consistency,
- Isolation,
- Durability.

Poziomy izolacji dotyczą litery:
```text
I = Isolation
```

Izolacja określa:
- jak bardzo jedna transakcja „widzi” działania innych,
- kiedy zmiany stają się widoczne,
- jakie konflikty są wykrywane.

Wyższa izolacja:
- zwiększa poprawność,
- ale zwykle zmniejsza concurrency.

Niższa izolacja:
- zwiększa throughput,
- ale pozwala na anomalie.

---

# READ COMMITTED — domyślny poziom PostgreSQL

Domyślnym poziomem izolacji w PostgreSQL jest:

```text
READ COMMITTED
```

To bardzo ważne, ponieważ wiele osób zakłada błędnie, że jedna transakcja widzi jeden stały snapshot danych.

W READ COMMITTED tak nie jest.

---

# Jak działa READ COMMITTED

Każde zapytanie SELECT:
- tworzy własny snapshot,
- widzi dane zatwierdzone przed rozpoczęciem konkretnego zapytania.

To oznacza, że:

```text
dwa SELECT-y w tej samej transakcji
mogą zwrócić różne wyniki
```

jeżeli:
- inna transakcja wykona COMMIT pomiędzy nimi.

---

# Przykład

Transakcja wykonuje:

```sql
SELECT balance FROM accounts WHERE id = 1;
```

i otrzymuje:

```text
1000
```

W międzyczasie:
- inna transakcja aktualizuje saldo,
- wykonuje COMMIT.

Drugi SELECT w pierwszej transakcji może już zwrócić:

```text
1200
```

mimo że:
- obydwa SELECT-y znajdują się w jednej transakcji.

To właśnie:
```text
non-repeatable read
```

---

# Non-repeatable Read

Non-repeatable read oznacza:

> ten sam SELECT wykonany dwa razy w tej samej transakcji zwraca różne dane.

Powód:
- inna transakcja zmodyfikowała rekord,
- wykonała COMMIT pomiędzy odczytami.

READ COMMITTED dopuszcza takie zachowanie.

---

# Phantom Read

Kolejną anomalią jest:

```text
phantom read
```

To sytuacja, gdy:
- ten sam warunek WHERE,
- zwraca inną liczbę rekordów.

---

# Przykład

Pierwszy SELECT:

```sql
SELECT COUNT(*)
FROM orders
WHERE created_at >= '2025-01-01';
```

zwraca:

```text
5
```

W międzyczasie:
- inna transakcja dodaje nowy rekord,
- wykonuje COMMIT.

Ten sam SELECT wykonany ponownie zwraca:

```text
6
```

Pojawił się:
```text
phantom
```

czyli nowy rekord spełniający warunek.

---

# Dirty Read w PostgreSQL

Warto podkreślić bardzo ważną rzecz:

PostgreSQL:
- nawet w READ COMMITTED,
- NIE pozwala na dirty reads.

To oznacza, że:
- transakcja nie zobaczy niezatwierdzonych danych innych transakcji.

Jeżeli:
- jedna transakcja wykona UPDATE,
- ale jeszcze nie zrobi COMMIT,

inne transakcje nadal widzą:
- poprzednią zatwierdzoną wersję danych.

To ogromnie ważna cecha MVCC w PostgreSQL.

---

# MVCC — Multi Version Concurrency Control

PostgreSQL używa:
```text
MVCC
```

czyli:
```text
Multi Version Concurrency Control
```

Zamiast:
- blokować wszystkie odczyty,
- baza przechowuje wiele wersji rekordów.

Każda transakcja:
- widzi odpowiedni snapshot,
- pracuje na swojej logicznej wersji danych.

Dzięki temu:
- SELECT-y nie blokują UPDATE,
- UPDATE nie blokują SELECT,
- concurrency jest bardzo wysoki.

---

# REPEATABLE READ w PostgreSQL

Kolejnym poziomem izolacji jest:

```text
REPEATABLE READ
```

W PostgreSQL działa on praktycznie jako:
```text
Snapshot Isolation
```

To oznacza:

> cała transakcja widzi jeden snapshot danych z momentu rozpoczęcia transakcji.

---

# Co daje REPEATABLE READ

W REPEATABLE READ:
- kolejne SELECT-y widzą te same dane,
- nie występują non-repeatable reads,
- nie występują klasyczne phantomy.

Nawet jeśli:
- inne transakcje wykonują COMMIT,
- aktualizują rekordy,
- dodają nowe dane,

transakcja nadal widzi:
- swój pierwotny snapshot.

---

# Snapshot Isolation

To bardzo ważny koncept.

Snapshot Isolation oznacza:
- transakcja działa na logicznej „fotografii” danych,
- nie widzi zmian wykonanych po starcie transakcji.

To daje:
- bardzo przewidywalne odczyty,
- dużą stabilność query,
- brak phantomów w PostgreSQL.

Jednak Snapshot Isolation nie jest pełnym SERIALIZABLE.

---

# Lost Update

Jedną z najważniejszych anomalii jest:

```text
lost update
```

To sytuacja, gdy:
- dwie transakcje odczytują tę samą wartość,
- obie wykonują obliczenia,
- jedna nadpisuje wynik drugiej.

---

# Przykład

Saldo:
```text
1000
```

Transakcja T1:
```text
1000 -> 900
```

Transakcja T2:
```text
1000 -> 800
```

Jeżeli:
- obie zapiszą wynik absolutny,
- jedna nadpisze drugą,

końcowe saldo może wynosić:
```text
800
```

albo:
```text
900
```

zamiast:
```text
700
```

---

# Dlaczego to jest niebezpieczne

Problem nie wynika z samego UPDATE.

Problem wynika z:
- odczytu do pamięci aplikacji,
- lokalnych obliczeń,
- późniejszego nadpisania rekordu.

To bardzo częsty błąd aplikacyjny.

---

# Bezpieczniejszy wzorzec

Znacznie lepiej wykonywać operacje bezpośrednio w bazie:

```sql
UPDATE accounts
SET balance = balance - 100
WHERE id = 1;
```

Wtedy PostgreSQL:
- serializuje konflikty na poziomie wiersza,
- aktualizuje aktualną wersję danych,
- zmniejsza ryzyko lost update.

---

# SELECT FOR UPDATE

Kolejnym mechanizmem ochrony jest:

```sql
SELECT ... FOR UPDATE
```

To:
- blokuje rekord,
- uniemożliwia równoległe UPDATE,
- wymusza oczekiwanie innych transakcji.

Przydaje się gdy:
1. aplikacja musi przeczytać dane,
2. sprawdzić invariant,
3. wykonać logikę biznesową,
4. dopiero potem zapisać wynik.

---

# Write Skew — problem Snapshot Isolation

Najbardziej podstępną anomalią Snapshot Isolation jest:

```text
write skew
```

To sytuacja, gdy:
- dwie transakcje czytają wspólny invariant,
- aktualizują różne rekordy,
- nie występuje bezpośredni konflikt row-level.

---

# Przykład lekarzy na dyżurze

Invariant:
```text
co najmniej jeden lekarz musi być na dyżurze
```

Początkowo:
- lekarz 1 = on_call,
- lekarz 2 = on_call.

Obie transakcje:
- widzą dwóch lekarzy,
- uznają że mogą zdjąć siebie z dyżuru.

T1:
```sql
UPDATE doctor_1 SET on_call = false;
```

T2:
```sql
UPDATE doctor_2 SET on_call = false;
```

Obie transakcje:
- modyfikują różne rekordy,
- więc PostgreSQL nie widzi klasycznego konfliktu write-write.

Końcowy stan:
```text
0 lekarzy na dyżurze
```

Invariant został złamany.

---

# Dlaczego Snapshot Isolation tego nie wykrywa

Ponieważ:
- nie ma konfliktu na tym samym rekordzie,
- każda transakcja działa na własnym snapshot,
- każda uważa swoje działanie za poprawne.

To właśnie:
```text
write skew
```

---

# SERIALIZABLE w PostgreSQL

Najwyższym poziomem izolacji jest:

```text
SERIALIZABLE
```

W PostgreSQL implementowany jako:

```text
SSI — Serializable Snapshot Isolation
```

---

# Co robi SERIALIZABLE

PostgreSQL:
- analizuje zależności pomiędzy transakcjami,
- wykrywa potencjalnie nieserializowalne wykonania,
- przerywa jedną z transakcji.

Typowy błąd:

```text
could not serialize access due to read/write dependencies
```

To oznacza:

> baza wykryła sytuację,
> która nie mogłaby wystąpić przy wykonaniu sekwencyjnym.

---

# Bardzo ważna zasada

W SERIALIZABLE aplikacja:
- MUSI obsługiwać retry.

To normalne zachowanie.

Transakcja:
- może zostać przerwana,
- aplikacja powinna uruchomić ją ponownie.

---

# SERIALIZABLE nie oznacza „wolno”

Bardzo częsty mit:
```text
SERIALIZABLE = zawsze bardzo wolne
```

To nie jest prawda.

Koszt zależy od:
- liczby konfliktów,
- długości transakcji,
- contention,
- workloadu.

Dla wielu systemów SERIALIZABLE jest całkowicie praktyczne.

---

# Najważniejsze anomalie

## READ COMMITTED

Możliwe:
- non-repeatable read,
- phantom read,
- lost update patterns.

Brak:
- dirty read.

---

## REPEATABLE READ / Snapshot Isolation

Brak:
- non-repeatable read,
- phantom read.

Możliwe:
- write skew.

---

## SERIALIZABLE

Najsilniejsza izolacja.

PostgreSQL:
- wykrywa nieserializowalne wykonania,
- przerywa konflikty,
- wymaga retry logic.

---

# Najważniejsza praktyczna zasada

Najważniejszym błędem projektowym jest:
- ignorowanie współbieżności,
- zakładanie że transakcje wykonują się „jedna po drugiej”.

W rzeczywistych systemach:
- wiele requestów działa równolegle,
- transakcje przeplatają się,
- invariants mogą zostać złamane.

Dlatego:
- poziom izolacji,
- locking,
- snapshot semantics,
- retry logic,
- projekt transakcji

są fundamentalną częścią projektowania backendów i systemów bazodanowych.
