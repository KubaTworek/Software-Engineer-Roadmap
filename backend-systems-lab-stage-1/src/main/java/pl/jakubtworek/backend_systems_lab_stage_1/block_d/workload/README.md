# Dobór Technologii pod Workload

# Architektura powinna wynikać z problemu, nie z trendów

Jednym z najczęstszych błędów przy projektowaniu systemów jest wybieranie technologii na podstawie ich popularności, marketingu albo tego, co aktualnie dominuje w dyskusjach branżowych. W praktyce bardzo wiele problemów produkcyjnych nie wynika z „błędów implementacyjnych”, lecz z fundamentalnego niedopasowania technologii do charakterystyki workloadu.

System może być napisany poprawnie, dobrze przetestowany i wdrożony przez kompetentny zespół, a mimo to cierpieć na chroniczne problemy z:
- wydajnością,
- spójnością danych,
- kosztami utrzymania,
- skalowaniem,
- niezawodnością.

Powodem często nie jest kod, lecz błędne założenie architektoniczne przy wyborze technologii.

Dobór technologii powinien być konsekwencją:
- wymagań biznesowych,
- charakterystyki workloadu,
- ograniczeń operacyjnych,
- oczekiwanej skali,
- wymagań consistency,
- rodzaju invariants.

Najpierw należy zrozumieć:
- jak wygląda ruch,
- jakie są proporcje odczytów do zapisów,
- jakie opóźnienia są akceptowalne,
- czy dane muszą być natychmiast spójne,
- jakie invariants muszą być zawsze zachowane,
- jakie awarie są dopuszczalne biznesowo.

Dopiero później dobiera się:
- bazę danych,
- model komunikacji,
- cache,
- sposób skalowania,
- storage,
- architekturę systemu.

Bardzo często organizacje robią odwrotnie:
1. wybierają modną technologię,
2. a potem próbują dopasować do niej problem.

To zwykle prowadzi do:
- skomplikowanej architektury,
- workaroundów,
- compensating transactions,
- race conditions,
- problemów operacyjnych.

---

# Workload jako punkt wyjścia

Podstawowym pytaniem powinno być:

> Jak system będzie używany?

Sama informacja „potrzebujemy bazy danych” praktycznie nic nie mówi.

Kluczowe znaczenie ma:
- read/write ratio,
- latency,
- throughput,
- consistency requirements,
- access patterns,
- rodzaj danych,
- expected scale.

---

# Read-heavy Workload

Systemy read-heavy wykonują ogromną liczbę odczytów i relatywnie niewiele zapisów.

## Typowe przykłady

- e-commerce,
- katalog produktów,
- streaming,
- public APIs,
- blogi,
- CMS,
- portale informacyjne.

## Priorytety

Najważniejsze stają się:
- niski latency,
- cache,
- skalowanie odczytów,
- throughput,
- szybkie query.

## Typowe rozwiązania

- PostgreSQL + read replicas,
- Redis cache,
- CDN,
- Elasticsearch,
- denormalizacja,
- precomputed views.

## Typowy stack

```text
Client
  ↓
CDN
  ↓
Redis Cache
  ↓
Read Replicas
  ↓
PostgreSQL
```

W takich systemach często można zaakceptować częściową eventual consistency.

Jeżeli licznik wyświetleń będzie opóźniony o kilka sekund:
- użytkownik zwykle tego nie zauważy,
- biznes nie ponosi dużej straty.

To oznacza, że system można zoptymalizować pod:
- throughput,
- availability,
- latency.

---

# Write-heavy Workload

Systemy write-heavy generują ogromną liczbę zapisów.

## Typowe przykłady

- telemetry,
- IoT,
- logging,
- analytics,
- event ingestion,
- monitoring.

## Priorytety

Najważniejsze są:
- write throughput,
- append-only storage,
- batching,
- partitioning,
- ingestion speed.

## Typowe rozwiązania

- Apache Kafka,
- ClickHouse,
- Cassandra,
- Time-series DB,
- stream processing.

## Typowy stack

```text
Producers
  ↓
Kafka
  ↓
Stream Processing
  ↓
ClickHouse
```

Próba realizacji takiego workloadu na klasycznej relacyjnej bazie danych bardzo szybko prowadzi do:
- lock contention,
- problemów z throughputem,
- wysokich kosztów skalowania,
- write bottlenecków.

---

# Strong Consistency vs Eventual Consistency

Jednym z najważniejszych pytań architektonicznych jest:

> Czy system wymaga silnej spójności danych?

---

# Strong Consistency

Silna spójność oznacza, że dane muszą być natychmiast poprawne.

## Typowe przypadki

- bankowość,
- płatności,
- księgowość,
- inventory management,
- systemy rezerwacji,
- billing.

## Priorytety

- correctness,
- atomicity,
- deterministic behavior,
- invariants.

## Typowe technologie

- PostgreSQL,
- MySQL,
- ACID databases.

## Mechanizmy

- transactions,
- constraints,
- locking,
- SERIALIZABLE isolation,
- SELECT FOR UPDATE.

## Przykład

Jeżeli system sprzeda:
- dwa bilety na to samo miejsce,
- albo pozwoli zejść saldu poniżej zera,

to problem nie jest „techniczny”.

To realny błąd biznesowy.

W takich systemach correctness jest ważniejsze niż:
- availability,
- global scalability,
- ultra-low latency.

---

# Eventual Consistency

Niektóre systemy mogą tolerować chwilową niespójność danych.

## Typowe przypadki

- social feed,
- rekomendacje,
- analytics,
- liczniki wyświetleń,
- cache.

## Priorytety

- scalability,
- availability,
- low latency,
- partition tolerance.

## Typowe technologie

- Cassandra,
- DynamoDB,
- Redis,
- event-driven systems.

## Ważna uwaga

Eventual consistency nie jest „darmowym skalowaniem”.

To świadoma zgoda na:
- czasową niespójność,
- reconciliation,
- retry logic,
- distributed complexity.

---

# Invariants jako fundament architektury

Najbardziej niedocenianym aspektem projektowania systemów są invariants.

## Czym są invariants?

To reguły, które zawsze muszą pozostać prawdziwe.

## Przykłady

- saldo nie może być ujemne,
- produkt nie może zostać sprzedany dwa razy,
- jedno miejsce może mieć jednego właściciela,
- numer faktury musi być unikalny.

Jeżeli system posiada silne invariants, storage musi umożliwiać:
- atomic writes,
- transactional guarantees,
- locking,
- consistency.

Próba implementowania takich wymagań na eventual consistency bardzo często kończy się:
- race conditions,
- compensating transactions,
- reconciliation jobs,
- distributed locking,
- trudnym debuggingiem.

---

# Relacyjne bazy danych są niedoceniane

Wiele nowoczesnych aplikacji nadal ma naturę relacyjną.

## Typowe systemy relacyjne

- ERP,
- CRM,
- marketplace,
- SaaS,
- billing,
- fintech.

Takie systemy operują na:
- relacjach,
- JOIN-ach,
- constraints,
- transactions,
- consistency.

Dlatego PostgreSQL bardzo często jest lepszym wyborem niż NoSQL.

---

# Problem z MongoDB i schema-less

Wiele organizacji wybiera MongoDB, ponieważ:
- łatwo zacząć,
- schema-less wydaje się wygodne,
- development początkowo jest szybki.

Jednak wraz ze wzrostem systemu zwykle okazuje się, że:
- dane mają relacje,
- potrzebne są constraints,
- consistency staje się ważna,
- pojawiają się JOIN-y.

W efekcie logika zaczyna migrować do aplikacji.

Zespół ręcznie implementuje funkcje, które relacyjne bazy oferują od dekad.

---

# Distributed Systems są bardzo kosztowne

Distributed systems rozwiązują konkretne problemy, ale wprowadzają ogromną złożoność.

## Koszty distributed systems

- observability,
- retries,
- debugging,
- synchronization,
- network partitions,
- deployment complexity,
- operational overhead,
- eventual consistency.

## Typowy anty-pattern

Startup mający kilka tysięcy użytkowników buduje:
- Kubernetes,
- Kafka,
- CQRS,
- event sourcing,
- mikroserwisy,
- multi-region replication.

Mimo że workload bez problemu obsłużyłby pojedynczy PostgreSQL.

To klasyczna przedwczesna optymalizacja.

---

# Przykłady Doboru Technologii

# 1. System bankowy

## Wymagania

- strong consistency,
- correctness,
- invariants,
- transactional guarantees.

## Dobór

- PostgreSQL,
- ACID,
- synchronous replication,
- row locking,
- transactional boundaries.

## Zły wybór

- Cassandra,
- eventual consistency,
- async write propagation.

---

# 2. Social Media Feed

## Wymagania

- ogromny throughput,
- niski latency,
- availability,
- skalowalność.

## Dobór

- Redis,
- Kafka,
- Cassandra,
- fan-out architecture.

## Dlaczego?

Eventual consistency jest akceptowalna.

---

# 3. System rezerwacji miejsc

## Wymagania

- brak double booking,
- strong consistency,
- atomic reservations.

## Dobór

- PostgreSQL,
- SELECT FOR UPDATE,
- SERIALIZABLE isolation,
- transactional writes.

---

# 4. Platforma telemetryczna

## Wymagania

- miliony write/sec,
- append-only,
- analytics.

## Dobór

- Kafka,
- ClickHouse,
- stream processing,
- partitioning.

---

# 5. E-commerce

## Wymagania

- dużo odczytów,
- inventory correctness,
- search,
- cache.

## Dobór

- PostgreSQL jako source of truth,
- Redis,
- Elasticsearch,
- read replicas.

---

# 6. Platforma analityczna

## Wymagania

- OLAP,
- agregacje,
- miliardy rekordów,
- batch analytics.

## Dobór

- ClickHouse,
- data lake,
- columnar storage,
- batch processing.

---

# Najważniejsze Wnioski

Dobra architektura nie polega na używaniu:
- najbardziej modnych technologii,
- największej liczby komponentów,
- distributed systems „na przyszłość”.

Dobra architektura to świadome dopasowanie technologii do:
- workloadu,
- invariants,
- consistency requirements,
- skali,
- możliwości operacyjnych zespołu.

Najczęściej niedocenianą cechą architektury jest prostota.

Bardzo wiele systemów działałoby:
- stabilniej,
- taniej,
- szybciej,
- bardziej przewidywalnie

na pojedynczym PostgreSQL niż na rozbudowanym distributed stacku budowanym „na przyszłość”.