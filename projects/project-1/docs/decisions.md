# Decyzje projektowe

## 0001 — Start jako zwykły monolit warstwowy

Projekt zaczyna się jako klasyczny monolit Spring Boot z warstwami:

* `controller`,
* `service`,
* `repository`.

Powód:

Celem projektu jest zrozumienie współbieżności, transakcji, JPA, wydajności SQL, działania JVM oraz mechanizmów Springa bez ukrywania podstawowych problemów za nadmierną architekturą.

Na tym etapie nie wprowadzamy mikroserwisów, brokerów wiadomości ani złożonego podziału modułowego. Projekt ma być zwykłym, czytelnym monolitem.

---

## 0002 — PostgreSQL jako źródło prawdy

Rezerwacje i dostępność miejsc są przechowywane w PostgreSQL.

Powód:

Ograniczona liczba miejsc oraz ryzyko oversellingu to problemy transakcyjne. Bazowy projekt powinien najpierw rozwiązać je mechanizmami relacyjnej bazy danych, zanim pojawią się cache, NoSQL albo przetwarzanie asynchroniczne.

PostgreSQL pozostaje źródłem prawdy dla:

* eventów,
* rezerwacji,
* klientów,
* puli dostępności,
* użytkowników aplikacji,
* refresh tokenów,
* audytu,
* komunikatów wychodzących.

---

## 0003 — Atomowy SQL update dla dostępności miejsc

Bazowy flow rezerwacji zmniejsza dostępność miejsc przez warunkowy update SQL.

Preferowana operacja:

```sql
UPDATE capacity_pools
SET available_capacity = available_capacity - 1
WHERE event_id = ?
  AND available_capacity > 0;
```

Powód:

To prosty i solidny baseline chroniący przed oversellingiem. Warunek biznesowy i modyfikacja stanu są wykonane jako jedna operacja po stronie bazy danych.

Dzięki temu nie ma osobnego okna czasowego między:

1. sprawdzeniem dostępności,
2. zmniejszeniem liczby miejsc.

Późniejsze etapy nadal implementują wariant naiwny i strategie lockowania, ale głównie jako materiał edukacyjny do porównania.

---

# Etap 2 — Concurrency

## Decyzja: produkcyjny flow rezerwacji preferuje atomowy update SQL

Dla głównego flow rezerwacji preferujemy atomowy update SQL:

```sql
UPDATE capacity_pools
SET available_capacity = available_capacity - 1
WHERE event_id = ?
  AND available_capacity > 0;
```

Powód:

Problem dostępności jest prostym licznikiem. Warunek `available_capacity > 0` i zmniejszenie wartości mogą być jedną operacją bazodanową.

To ogranicza ryzyko:

* race condition,
* lost update,
* oversellingu,
* błędnego check-then-act.

## Strategie zostawione edukacyjnie

Pozostałe strategie zostają w kodzie jako materiał porównawczy:

* `NaiveReservationService` — pokazuje overselling i lost update,
* `SynchronizedReservationService` — pokazuje lokalną ochronę w jednej JVM,
* `OptimisticLockingReservationService` — pokazuje użycie `@Version` i retry,
* `PessimisticLockingReservationService` — pokazuje odpowiednik `SELECT ... FOR UPDATE`,
* `AtomicSqlReservationService` — pokazuje preferowaną strategię dla tego projektu.

## Trade-off

`AtomicSqlReservationService` jest najlepszym baseline’em dla tego konkretnego problemu, ale nie oznacza, że zawsze zastępuje wszystkie inne strategie.

Jeżeli logika rezerwacji stanie się bardziej złożona i będzie zależała od wielu tabel lub reguł biznesowych, może być potrzebne połączenie:

* constraintów bazodanowych,
* transakcji,
* lockowania,
* retry,
* osobnego modelu płatności/rezerwacji.

---

# Etap 3 — Asynchroniczność

## Decyzja: async pozostaje wewnątrz monolitu

Na tym etapie projekt używa przetwarzania asynchronicznego wewnątrz jednej aplikacji:

* `ThreadPoolExecutor`,
* `ScheduledExecutorService`,
* `CompletableFuture`.

Decyzje:

* zostaje jedna aplikacja Spring Boot,
* zostaje jedna relacyjna baza danych,
* nie wprowadzamy jeszcze Kafka ani RabbitMQ,
* side-effecty asynchroniczne są modelowane przez lokalne serwisy i rekordy w bazie,
* używamy jawnie zdefiniowanych executorów zamiast `ForkJoinPool.commonPool()`.

Powód:

Ten etap dotyczy podstaw programowania asynchronicznego i współbieżności w Javie, a nie systemów rozproszonych. Dodanie brokera na tym etapie ukryłoby główne cele edukacyjne za infrastrukturą.

## Trade-off

Praca asynchroniczna wykonywana w pamięci procesu może zostać utracona, jeśli JVM zakończy się po potwierdzeniu rezerwacji, ale przed zakończeniem side-effectów.

To jest akceptowalne w tym etapie edukacyjnym.

W wersji produkcyjnej prawdopodobnie należałoby użyć transactional outbox pattern.

---

## Decyzja: timeout płatności oznacza `PAYMENT_TIMEOUT`

Jeżeli walidacja płatności jest wolna albo kończy się błędem technicznym, rezerwacja zostaje oznaczona jako:

```java
PAYMENT_TIMEOUT
```

Powód:

System nie powinien potwierdzać rezerwacji, jeśli nie wiadomo, czy płatność została poprawnie zwalidowana.

Status `PAYMENT_TIMEOUT` jawnie pokazuje stan niepewny i pozwala go testować.

## Trade-off

W większym systemie stan płatności prawdopodobnie powinien być osobnym modelem, niezależnym od statusu rezerwacji.

W tym projekcie stan płatności jest celowo uproszczony i trzymany w statusie rezerwacji, bo projekt nadal jest monolitem edukacyjnym.

---

# Etap 4 — Spring pod maską

## Decyzja: pułapki Springa są odizolowane w osobnym pakiecie

Przykłady błędnego albo zaskakującego działania Springa są zaimplementowane w pakiecie:

```text
service.pitfall
```

i wystawione przez endpointy:

```text
/api/spring-pitfalls
```

Powód:

Te przykłady są celowo edukacyjne. Nie powinny zanieczyszczać głównego flow rezerwacji.

Główny flow biznesowy powinien pozostać prosty i przewidywalny, a Etap 4 osobno pokazuje:

* granice proxy,
* granice transakcji,
* self-invocation,
* lazy loading poza persistence contextem,
* AOP,
* lifecycle beanów.

## Trade-off

Self-injection przez `ObjectProvider` jest użyty tylko po to, żeby pokazać różnicę między wywołaniem przez `this` a wywołaniem przez proxy Springa.

Nie jest to domyślna rekomendacja projektowa.

---

# Etap 5 — SQL i performance

## Decyzja: pełny dataset nie jest częścią zwykłych testów

Pełny dataset:

* 100 000 eventów,
* 1 000 000 rezerwacji,

jest generowany tylko przez profil:

```text
performance-seed
```

Zwykłe testy mają sprawdzać poprawność zapytań i endpointów, a nie benchmarkować bazę danych.

Benchmarki, pomiary i `EXPLAIN ANALYZE` są osobnym, świadomym krokiem wykonywanym na PostgreSQL.

## Powód

Testy funkcjonalne powinny być szybkie, przewidywalne i możliwe do uruchomienia często.

Duży dataset służy do analizy wydajności, nie do codziennej walidacji logiki.

---

## Decyzja: indeksy są dobierane pod access pattern

Indeksy są dobierane pod konkretne zapytania aplikacji.

Dla wyszukiwania eventów używamy indeksu:

```sql
(city, category, starts_at)
```

bo zapytanie filtruje po:

* `city`,
* `category`,

a potem wykonuje zakres albo sortowanie po:

* `starts_at`.

Dla widoku rezerwacji organizacji używamy:

```sql
(organization_id, status, created_at DESC)
```

Dla historii rezerwacji klienta używamy:

```sql
(customer_id, created_at DESC, id DESC)
```

Powód:

Indeks nie powinien być dodawany “na oko”. Powinien odpowiadać rzeczywistemu zapytaniu i kolejności filtrów/sortowania.

---

## Decyzja: offset pagination zostaje obok keyset pagination

Projekt pokazuje oba podejścia:

* offset pagination,
* keyset pagination.

Offset pagination jest prosta dla UI i pozwala przejść do strony N.

Problem:

Dla głębokich stron skaluje się słabo, bo baza musi pominąć coraz więcej rekordów.

Keyset pagination jest mniej wygodna, ale zwykle stabilniejsza wydajnościowo dla feedów i historii.

## Trade-off

Offset zostaje, bo jest prosty i popularny.

Keyset zostaje, bo pokazuje lepszy wzorzec dla dużych tabel i sortowania po indeksie.

---

## Decyzja: N+1 pokazujemy celowo

Endpoint:

```text
n-plus-one
```

zostaje jako edukacyjna pułapka.

Powód:

Problem N+1 jest jednym z najczęstszych problemów w aplikacjach JPA/Hibernate. Warto mieć endpoint, który celowo go pokazuje.

Wariant produkcyjny powinien używać jednego z rozwiązań:

* DTO projection,
* `fetch join`,
* `@EntityGraph`,
* batch fetching — zależnie od przypadku.

---

# Etap 6 — JVM i profilowanie

## Decyzja: endpointy profilujące są syntetyczne i edukacyjne

Endpointy:

```text
/api/profiling/**
```

nie są częścią produkcyjnego API.

Ich celem jest wygenerowanie kontrolowanego obciążenia, które można obserwować przez:

* JFR,
* VisualVM,
* IntelliJ Profiler,
* GC logs,
* PostgreSQL `EXPLAIN ANALYZE`.

## Powód

Nie da się sensownie optymalizować JVM “na czuja”.

Te endpointy mają stworzyć warunki do pomiaru:

* CPU hotspotów,
* allocation rate,
* GC pauses,
* lock contention,
* liczby wątków,
* latency,
* throughputu,
* wpływu heap size,
* wpływu G1 vs ZGC,
* wpływu rozmiaru puli wątków.

---

## Decyzja: JMH jest osobnym projektem Mavenowym

Benchmarki JMH są trzymane w katalogu:

```text
benchmarks
```

Powód:

Zwykłe:

```bash
mvn test
```

nie powinno uruchamiać mikrobenchmarków.

Testy aplikacyjne sprawdzają tylko, czy scenariusze profilujące da się uruchomić.

Rzeczywiste pomiary wykonuje się przez:

* JMH,
* profiler,
* JFR,
* GC logs.

---

## Decyzja: nie mieszamy testów funkcjonalnych z pomiarami performance

Test:

```text
JvmProfilingStage6IntegrationTest
```

nie udowadnia wydajności.

Potwierdza tylko, że scenariusze profilujące działają i można je uruchomić.

Wnioski performance muszą pochodzić z narzędzi pomiarowych:

* JFR,
* GC logs,
* JMH,
* `EXPLAIN ANALYZE`.

## Powód

Test integracyjny nie jest benchmarkiem.

Benchmark musi uwzględniać warmup, JIT, izolację środowiska, powtarzalność i właściwą metodologię.

---

# Etap 7 — Security i autoryzacja

## Decyzja: security pokazujemy jako autoryzację po danych, nie tylko role

Etap 7 nie kończy się na prostym:

```java
hasRole("ADMIN")
```

Projekt pokazuje reguły oparte o dane:

* customer widzi tylko swoje rezerwacje,
* manager widzi eventy swojej organizacji,
* HR widzi pracowników swojej organizacji,
* support widzi status operacyjny, ale nie pełne dane płatności,
* admin organizacji zarządza użytkownikami tylko w swoim tenantcie.

## Powód

Sama rola nie wystarcza.

Użytkownik z rolą `EVENT_MANAGER` nie powinien automatycznie widzieć eventów wszystkich organizacji.

Użytkownik z rolą `HR` nie powinien widzieć pracowników z innych tenantów.

---

## Decyzja: wcześniejsze endpointy edukacyjne pozostają publiczne

Istniejące endpointy z wcześniejszych etapów pozostają publiczne, a Etap 7 dodaje osobny obszar:

```text
/api/secure/**
```

Powód:

Projekt jest etapowy.

Gdyby wszystkie wcześniejsze endpointy zostały nagle zabezpieczone, starsze testy:

* MVP,
* concurrency,
* async,
* Spring pitfalls,
* performance,

przestałyby sprawdzać to, co miały sprawdzać.

Etap 7 pokazuje security na osobnych endpointach, bez psucia wcześniejszych etapów.

---

## Decyzja: refresh tokeny są przechowywane jako hash

Refresh tokeny są przechowywane w bazie wyłącznie jako hash SHA-256.

Powód:

Wyciek bazy danych nie powinien dawać gotowych refresh tokenów.

To nadal uproszczenie edukacyjne, ale lepsze niż zapis tokenów jawnie.

## Uwaga

Dla haseł użytkowników nie używamy zwykłego SHA-256.

Hasła powinny być hashowane algorytmem typu:

* BCrypt,
* Argon2,
* PBKDF2.

SHA-256 jest akceptowalny tutaj dlatego, że refresh token jest długi, losowy i ma wysoką entropię.

---

## Decyzja: różne role mają różne reguły oparte o dane

Role w projekcie:

* `CUSTOMER`,
* `EVENT_MANAGER`,
* `ORG_ADMIN`,
* `HR`,
* `SUPPORT`.

Każda z nich ma inne zasady dostępu.

Powód:

Autoryzacja musi zależeć nie tylko od roli, ale też od danych:

* kto jest właścicielem rezerwacji,
* do jakiej organizacji należy użytkownik,
* do jakiej organizacji należy event,
* czy użytkownik działa w swoim tenantcie,
* czy zakres danych jest odpowiedni dla danej roli.

Przykład:

Support może zobaczyć status operacyjny płatności, ale nie powinien widzieć pełnych danych płatniczych.

---

# Etap 8 — NoSQL i cache

## Decyzja: Redis i MongoDB są dodatkami do monolitu

Redis i MongoDB nie są nowymi źródłami prawdy.

PostgreSQL nadal odpowiada za spójność:

* rezerwacji,
* dostępności,
* płatności,
* użytkowników,
* tokenów.

Redis i MongoDB są używane świadomie jako dodatkowe narzędzia do konkretnych problemów odczytowych i technicznych.

---

## Decyzja: Redis jako key-value store z TTL

Redis został użyty tam, gdzie naturalny jest model:

```text
klucz -> wartość -> TTL
```

Przykłady użycia:

* cache detali eventu,
* snapshot dostępności,
* rate limiting,
* tymczasowe holdy rezerwacyjne.

## Ważne ograniczenie

Snapshot dostępności może być chwilowo nieaktualny.

Nie wolno sprzedawać miejsc wyłącznie na podstawie cache.

Finalna decyzja o rezerwacji miejsca musi nadal przejść przez PostgreSQL i atomowy update dostępności.

---

## Decyzja: MongoDB jako dokumentowy read model

MongoDB został użyty jako denormalizowany read model:

```text
EventSearchDocument
```

Dokument jest szybki do odczytu i dopasowany do access patternu wyszukiwarki eventów.

Może zawierać dane z kilku relacyjnych źródeł:

* event,
* organization,
* capacity pool,
* agregacje rezerwacji po statusach.

## Trade-off

Dokument może być nieaktualny do czasu rebuilda.

To celowo pokazuje:

* eventual consistency,
* problem read-your-writes,
* różnicę między źródłem prawdy a read modelem.

---

## Decyzja: distributed lock w Redisie nie jest domyślną strategią

Distributed lock w Redisie nie jest domyślną strategią ochrony przed oversellingiem.

Powód:

W tym projekcie problem oversellingu najlepiej rozwiązuje PostgreSQL przez atomowy update.

Redis może być użyteczny do cache, TTL i rate limitingu, ale nie powinien zastępować transakcyjnego źródła prawdy dla rezerwacji.

Domyślna poprawna strategia pozostaje:

```sql
UPDATE capacity_pools
SET available_capacity = available_capacity - 1
WHERE event_id = ?
  AND available_capacity > 0;
```

---

# Podsumowanie

Projekt świadomie zaczyna jako zwykły monolit.

Nie chodzi o pokazanie najmodniejszej architektury, tylko o zrozumienie realnych problemów backendowych:

* transakcji,
* race condition,
* lost update,
* lockowania,
* asynchroniczności,
* proxy Springa,
* lazy loadingu,
* SQL performance,
* profilowania JVM,
* autoryzacji po danych,
* cache i read modeli.

Najważniejsza decyzja techniczna pozostaje stabilna przez kolejne etapy:

**PostgreSQL jest źródłem prawdy, a dla dostępności miejsc bazowym zabezpieczeniem jest atomowy update SQL.**
