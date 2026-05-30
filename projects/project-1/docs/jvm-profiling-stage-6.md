# Etap 6 — JVM i profilowanie

Ten etap nie ma udowodnić, że aplikacja jest szybka. Ma nauczyć mierzenia: CPU, alokacji, GC, lock contention, liczby wątków, latency, throughput oraz wpływu ustawień JVM.

## Co zostało dodane

### Endpointy profilujące

Wszystkie endpointy są pod `/api/profiling`.

```http
POST /api/profiling/events/{eventId}/short-reservations?requests=1000
POST /api/profiling/events/{eventId}/parallel-reservations?requests=1000&threads=64
GET  /api/profiling/organizations/{organizationId}/report
GET  /api/profiling/allocations?objects=500000
GET  /api/profiling/lock-contention?threads=64&incrementsPerThread=100000
GET  /api/profiling/thread-pool?threads=8&tasks=500&workloadType=CPU
GET  /api/profiling/thread-pool?threads=64&tasks=500&workloadType=IO
GET  /api/profiling/big-decimal-allocation?iterations=500000
```

### Testy sanity

Te testy nie są benchmarkiem. Sprawdzają tylko, że scenariusze profilujące działają i są bezpieczne do uruchomienia.

```bash
mvn test -Dtest=JvmProfilingStage6IntegrationTest
mvn test -Dtest=ApiJvmProfilingStage6IntegrationTest
```

## Jak uruchamiać profilowanie aplikacji

### 1. Uruchom bazę

```bash
docker compose up -d
```

### 2. Opcjonalnie wygeneruj większy dataset

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=performance-seed
```

### 3. Uruchom aplikację z JFR

G1 jako punkt odniesienia:

```bash
java \
  -Xms512m \
  -Xmx512m \
  -XX:+UseG1GC \
  -XX:StartFlightRecording=filename=docs/jvm-profiling/g1-profile.jfr,duration=120s,settings=profile \
  -jar target/booking-capacity-platform-0.1.0-SNAPSHOT.jar
```

ZGC dla porównania pauz GC:

```bash
java \
  -Xms512m \
  -Xmx512m \
  -XX:+UseZGC \
  -XX:StartFlightRecording=filename=docs/jvm-profiling/zgc-profile.jfr,duration=120s,settings=profile \
  -jar target/booking-capacity-platform-0.1.0-SNAPSHOT.jar
```

Mniejszy heap, żeby celowo wywołać presję GC:

```bash
java \
  -Xms128m \
  -Xmx128m \
  -XX:+UseG1GC \
  -XX:StartFlightRecording=filename=docs/jvm-profiling/small-heap.jfr,duration=120s,settings=profile \
  -jar target/booking-capacity-platform-0.1.0-SNAPSHOT.jar
```

Logi GC:

```bash
java \
  -Xms512m \
  -Xmx512m \
  -Xlog:gc*,safepoint:file=docs/jvm-profiling/gc.log:time,uptime,level,tags \
  -jar target/booking-capacity-platform-0.1.0-SNAPSHOT.jar
```

## Co obserwować w JFR / VisualVM / IntelliJ Profiler

### Dużo krótkich rezerwacji

Endpoint:

```http
POST /api/profiling/events/{eventId}/short-reservations?requests=1000
```

Obserwuj:

- `JdbcTemplate` / Hibernate / JDBC latency,
- koszt transakcji,
- allocation rate przy DTO/encjach,
- młode GC,
- czy bottleneck jest w JVM, czy w bazie.

### Dużo równoległych requestów

Endpoint:

```http
POST /api/profiling/events/{eventId}/parallel-reservations?requests=1000&threads=64
```

Obserwuj:

- liczbę wątków runnable vs waiting,
- oczekiwanie na connection pool,
- blokady w DB,
- latency przy rosnącej liczbie wątków,
- czy więcej wątków faktycznie zwiększa throughput.

### Raport organizacji

Endpoint:

```http
GET /api/profiling/organizations/{organizationId}/report
```

Obserwuj:

- `EXPLAIN ANALYZE`,
- index scan vs sequential scan,
- hash aggregate,
- sort,
- alokacje przy mapowaniu wyników.

### Endpoint z N+1

Endpointy z Etapu 5:

```http
GET /api/events/{eventId}/reservations/n-plus-one
GET /api/events/{eventId}/reservations/fetch-join
GET /api/events/{eventId}/reservations/entity-graph
```

Obserwuj:

- liczbę zapytań SQL,
- czas JDBC,
- różnicę między lazy loadingiem a `fetch join` / `@EntityGraph`.

### Endpoint z dużą liczbą alokacji

Endpoint:

```http
GET /api/profiling/allocations?objects=500000
```

Obserwuj:

- allocation rate,
- `Allocation in new TLAB`,
- `Allocation outside TLAB`,
- young GC,
- obiekty najczęściej alokowane.

### Endpoint z contention na locku

Endpoint:

```http
GET /api/profiling/lock-contention?threads=64&incrementsPerThread=100000
```

Obserwuj:

- `Java Monitor Blocked`,
- owner locka,
- czas blokowania,
- spadek throughput przy wzroście liczby wątków.

## JMH

Benchmarki są w osobnym katalogu `benchmarks`, żeby zwykłe `mvn test` nie odpalało mikrobenchmarków.

Uruchomienie:

```bash
cd benchmarks
mvn clean package
java -jar target/benchmarks.jar
```

Wybrany benchmark:

```bash
java -jar target/benchmarks.jar StreamVsLoopBenchmark
java -jar target/benchmarks.jar MoneyBenchmark
java -jar target/benchmarks.jar CounterBenchmark
```

Dodane benchmarki:

- `CollectionIterationBenchmark` — `ArrayList` vs `LinkedList`, lokalność pamięci.
- `StreamVsLoopBenchmark` — stream vs pętla.
- `MoneyBenchmark` — `BigDecimal` vs `long` w minimalnej jednostce pieniądza.
- `CounterBenchmark` — `synchronized` vs `AtomicLong` vs `LongAdder`.
- `ObjectPoolingBenchmark` — zwykła alokacja vs naiwny object pool.
- `PolymorphismBenchmark` — monomorphic vs megamorphic call site.
- `FalseSharingBenchmark` — false sharing vs padding.

## Ważne zasady interpretacji

1. Pojedynczy wynik z jednego uruchomienia nie jest dowodem.
2. Porównuj wyniki na tym samym sprzęcie i tej samej wersji JDK.
3. Najpierw profiluj, potem optymalizuj.
4. Nie używaj `System.nanoTime()` jako zamiennika JMH dla mikrobenchmarków.
5. Nie uznawaj streamów, `BigDecimal`, locków ani object poolingu za złe same w sobie. Mierz kontekst.
6. W aplikacji webowej wynik może być ograniczony przez DB, connection pool albo sieć, nie przez JVM.

## Definition of done dla Etapu 6

- Masz co najmniej jeden plik `.jfr` dla scenariusza z alokacjami.
- Masz co najmniej jeden plik `.jfr` dla scenariusza z lock contention.
- Porównałeś G1 i ZGC na tym samym scenariuszu.
- Porównałeś przynajmniej dwa rozmiary heapu przez `-Xms` i `-Xmx`.
- Uruchomiłeś benchmarki JMH i zapisałeś wyniki.
- Dla każdego wniosku potrafisz wskazać źródło: JFR, GC log, JMH albo `EXPLAIN ANALYZE`.
