# Roadmapa rozwoju: Java Backend Mid → Senior → Top Senior

Ta roadmapa prowadzi od solidnego poziomu Mid do Seniora i dalej w stronę dojrzałego inżyniera backendowego. Nie chodzi o „zaliczenie technologii”, tylko o zbudowanie umiejętności podejmowania świadomych decyzji technicznych, projektowania systemów, diagnozowania problemów produkcyjnych i pracy z realnymi ograniczeniami biznesowymi.

## Założenia

- Stack główny: **Java, Spring, relacyjne bazy danych, systemy rozproszone**.
- Projekty są ważniejsze niż sama teoria.
- Każdy etap powinien kończyć się artefaktami: README, testami, wynikami load testów, diagramami, decyzjami architektonicznymi i wnioskami.
- Seniority nie oznacza znajomości większej liczby frameworków, tylko lepsze decyzje, lepszą diagnostykę i większy wpływ na system oraz zespół.

---

# Etap 1: 0–6 miesięcy — solidny Mid z fundamentem pod Seniora

## Cel etapu

Po tym etapie powinieneś:

- rozumieć i kontrolować wydajność aplikacji na poziomie CPU, JVM, GC i DB,
- pisać kod odporny na konkurencję, race condition i błędy transakcyjne,
- traktować Springa jako narzędzie, a nie magię,
- pisać kod testowalny i możliwy do refaktoryzacji bez przepisywania systemu od zera.

---

## Blok A — [Java Concurrency](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/README.md)

### Zakres

- `ExecutorService`:
  - pule wątków,
  - kolejki zadań,
  - strategie odrzucania zadań,
  - dobór rozmiaru puli do typu pracy: CPU-bound vs IO-bound.
- `ForkJoinPool`:
  - kiedy ma sens,
  - kiedy pogarsza sytuację,
  - różnica między pracą dzieloną rekurencyjnie a zwykłym async processingiem.
- `CompletableFuture`:
  - fan-out / fan-in,
  - timeouty,
  - obsługa błędów,
  - fallbacki,
  - propagacja wyjątków.
- Typowe problemy współbieżności:
  - race condition,
  - lost update,
  - overselling,
  - deadlock,
  - visibility,
  - `volatile`,
  - happens-before.

### Kryteria ukończenia

- Potrafisz zreprodukować race condition w teście.
- Potrafisz celowo wywołać deadlock, a potem go naprawić.
- Potrafisz wyjaśnić mechanizm błędu, a nie tylko wskazać fix.
- Umiesz dobrać model równoległości do problemu zamiast używać async „bo wygląda nowocześnie”.

### Materiały

**Książki**

- *Java Concurrency in Practice*
- *Effective Java*

**Kursy / materiały wideo**

- Pluralsight — Java Concurrency
- Materiały Briana Goetza i José Paumarda

---

## Blok B — [JVM i profilowanie](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/README.md)

### Zakres

- Heap vs stack:
  - co trafia gdzie,
  - kiedy alokacje stają się problemem,
  - jak czytać objawy presji na pamięć.
- Garbage Collection:
  - G1 jako rozsądny default,
  - kiedy ZGC ma sens,
  - kiedy ZGC jest przerostem formy,
  - pause time vs throughput.
- Profilowanie:
  - CPU hotspots,
  - allocation rate,
  - allocation churn,
  - JFR,
  - flamegraph,
  - bottlenecki w aplikacji i bazie danych.

### Kryteria ukończenia

- Potrafisz odpowiedzieć na pytanie „dlaczego to jest wolne?” na podstawie danych.
- Umiesz pokazać profil CPU lub alokacji i wyciągnąć z niego konkretne wnioski.
- Nie diagnozujesz wydajności wyłącznie intuicją.

### Materiały

**Książki**

- *Java Performance*
- *Optimizing Java*

**Kursy / talks**

- Oracle JFR & JVM Internals talks
- JetBrains JVM Profiling videos

---

## Blok C — [Spring pod maską](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/README.md)

### Zakres

- Lifecycle beanów:
  - kiedy powstaje bean,
  - kiedy powstaje proxy,
  - jakie skutki mają proxy boundaries.
- `@Transactional`:
  - `REQUIRED`,
  - `REQUIRES_NEW`,
  - `NESTED`,
  - isolation levels,
  - self-invocation,
  - proxy boundaries,
  - rollback rules.
- Security:
  - JWT access token / refresh token,
  - rotacja refresh tokenów,
  - OAuth2 basics,
  - resource server,
  - authorization server w minimalnym zakresie,
  - autoryzacja oparta o dane,
  - `@PreAuthorize` i SpEL.
- Validation & errors:
  - globalny exception handling,
  - mapowanie błędów na HTTP,
  - spójny error contract,
  - brak wycieków informacji w komunikatach błędów.

### Kryteria ukończenia

- Potrafisz pokazać przypadek, w którym `@Transactional` nie działa, i wyjaśnić dlaczego.
- Rozumiesz, że autoryzacja nie kończy się na rolach.
- Potrafisz zaimplementować dostęp zależny od danych: właściciel, manager, HR, organizacja, tenant.

### Materiały

**Książki**

- *Spring in Action*
- *Spring Microservices in Action*

**Kursy**

- Spring Academy
- Spring Core
- Spring Security

---

## Blok D — [Bazy danych i performance](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/README.md)

### Zakres

- Indeksy B-tree:
  - kiedy pomagają,
  - kiedy szkodzą,
  - koszt zapisu vs koszt odczytu.
- Composite indexes:
  - kolejność kolumn,
  - selectivity,
  - wpływ na plan zapytania.
- `EXPLAIN ANALYZE`:
  - sequential scan,
  - index scan,
  - nested loop,
  - hash join,
  - sort,
  - koszt rzeczywisty vs estymowany.
- Transakcje:
  - `READ_COMMITTED`,
  - `REPEATABLE_READ`,
  - optimistic locking,
  - pessimistic locking,
  - spójność vs throughput.
- Problem N+1:
  - rozpoznanie,
  - `fetch join`,
  - entity graph,
  - batch fetching,
  - sytuacje, w których naprawa N+1 nie jest najlepszym trade-offem.

### Kryteria ukończenia

- Potrafisz dobrać isolation level i locking do konkretnego problemu.
- Umiesz poprawić zapytanie lub indeks na podstawie planu wykonania.
- Potrafisz uzasadnić kompromis między spójnością a przepustowością.

### Materiały

**Książki**

- *Designing Data-Intensive Applications*
- *SQL Performance Explained*

**Kursy / strony / talks**

- Use The Index, Luke!
- PostgreSQL EXPLAIN talks
- MySQL EXPLAIN talks

---

## Blok E — [Clean Code i testowalność](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_e/README.md)

### Zakres

- Refaktoryzacja istniejącego kodu bez rewrite’u.
- Granice odpowiedzialności klas:
  - domain,
  - application,
  - infrastructure,
  - adapters.
- Testowalność:
  - unit testy bez Spring Context,
  - testy integracyjne tam, gdzie faktycznie są potrzebne,
  - mockowanie granic systemu zamiast mockowania wszystkiego,
  - testy regresyjne,
  - testy negatywne.

### Kryteria ukończenia

- Refaktor zwiększa czytelność i testowalność bez zmiany zachowania systemu.
- Testy łapią regresje, a nie tylko happy path.
- Potrafisz uzasadnić, dlaczego dany test jest unit, integration albo end-to-end.

### Materiały

**Książki**

- *Clean Code*
- *Working Effectively with Legacy Code*
- *Clean Architecture*

**Kursy / talks**

- Refactoring Guru
- Materiały Martina Fowlera o refaktoryzacji i testowalności

---

# Etap 2: 6–12 miesięcy — Mid+ → Senior

## Temat przewodni

Świadome decyzje architektoniczne, niezawodne integracje i umiejętność pracy bliżej produkcji.

## Cel etapu

Po tym etapie powinieneś:

- nie mylić architektury z liczbą serwisów,
- rozumieć asynchroniczność, spójność i awarie,
- umieć zaprojektować, wdrożyć i utrzymać system,
- bronić decyzji technicznej: dlaczego tak, a nie inaczej.

---

## Blok A — [Architektura: myślenie, nie frameworki](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/README.md)

### Zakres

- Monolit vs mikroserwisy:
  - kiedy monolit jest najlepszą opcją,
  - modular monolith,
  - koszty mikroserwisów: operacyjne, poznawcze, organizacyjne.
- DDD praktycznie:
  - bounded context,
  - język domenowy,
  - ownership,
  - aggregate,
  - invarianty,
  - granice transakcji.
- Hexagonal / Clean Architecture:
  - domena bez Springa,
  - use case’y w warstwie application,
  - adaptery jako szczegół implementacyjny.

### Kryteria ukończenia

- Potrafisz wskazać konkretne punkty bólu, przy których modular monolith przestaje wystarczać.
- Rozumiesz, co boli bardziej: wspólna baza czy sieć, latency i eventual consistency.
- Umiesz rozdzielić argumenty techniczne od organizacyjnych.

### Materiały

**Książki obowiązkowe**

- *Implementing Domain-Driven Design*
- *Domain-Driven Design*
- *Clean Architecture*

**Talks / artykuły**

- Martin Fowler — *Monolith First*
- InfoQ — bounded context & modular monolith

---

## Blok B — [Integracje i asynchroniczność](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/README.md)

### Zakres

Wybierz jeden broker i poznaj go dogłębnie:

- Kafka albo RabbitMQ.

Następnie przećwicz:

- event-driven flow:
  - publish,
  - consume,
  - side effects.
- Idempotencja:
  - deduplikacja po `eventId`,
  - bezpieczne ponowienie operacji,
  - exactly-once jako mit, nie cel.
- Eventual consistency:
  - stany przejściowe, np. `PENDING`,
  - kompensacje,
  - obsługa opóźnionych eventów.
- Niezawodność:
  - retry,
  - backoff,
  - jitter,
  - DLQ,
  - replay,
  - outbox pattern.

### Kryteria ukończenia

System nie psuje danych, gdy:

- event przyjdzie dwa razy,
- konsument padnie w połowie operacji,
- broker ma chwilowy problem,
- retry odpali się równolegle,
- eventy przyjdą z opóźnieniem.

### Materiały

**Książki**

- *Designing Data-Intensive Applications*
- *Building Event-Driven Microservices*

**Kursy / materiały**

- Confluent — Kafka Fundamentals
- RabbitMQ — Core Concepts & Reliability
- Talks o idempotent consumers, retry storms i backpressure

---

## Blok C — [DevOps: praktyczne minimum](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/README.md)

### Zakres

- Docker:
  - multi-stage build,
  - małe obrazy,
  - szybkie buildy,
  - sensowny `.dockerignore`.
- Kubernetes:
  - Pod,
  - Deployment,
  - Service,
  - ConfigMap,
  - Secret,
  - readiness probe,
  - liveness probe.
- CI/CD:
  - build,
  - test,
  - build image,
  - opcjonalny deploy do środowiska testowego.
- Środowisko lokalne:
  - kind albo minikube.

### Kryteria ukończenia

- Rozumiesz, co dzieje się po `kubectl apply`.
- Wiesz, gdzie sprawdzić problem, gdy aplikacja działa lokalnie, ale nie działa na klastrze.
- Umiesz odróżnić problem aplikacji, konfiguracji, sieci i infrastruktury.

### Materiały

**Książki obowiązkowe**

- *Kubernetes Up & Running*

**Kursy / dokumentacja**

- KodeKloud
- Docker docs — multi-stage build
- Kubernetes docs

---

# Etap 3: 12–24 miesiące — Senior

## Temat przewodni

Projektowanie systemów, które działają pod obciążeniem, są obserwowalne i dają się sensownie utrzymać w chmurze.

## Cel etapu

Po tym etapie powinieneś:

- projektować system na tablicy i bronić decyzji,
- używać metryk, logów i trace’ów zamiast zgadywania,
- rozumieć skalę, awarie i koszty w chmurze,
- myśleć w kategoriach SLO, degradacji i trade-offów.

---

## Blok A — [System Design: skalowanie i odporność](./src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/README.md)

### Zakres

- Skalowanie:
  - pionowe vs poziome,
  - stateless API,
  - autoscaling,
  - bottlenecki przy wzroście RPS.
- Caching:
  - Redis,
  - TTL,
  - eviction,
  - LRU vs LFU,
  - cache stampede,
  - single-flight,
  - jitter.
- Rate limiting:
  - per IP,
  - per API key,
  - token bucket,
  - sliding window.
- Resilience:
  - circuit breaker,
  - retry,
  - backoff,
  - jitter,
  - graceful degradation.

### Kryteria ukończenia

Potrafisz narysować system i powiedzieć:

> Przy X RPS padnie ten element, przy Y RPS padnie inny, a wiem to na podstawie konkretnych założeń i metryk.

### Materiały

**Książki**

- *Designing Data-Intensive Applications*
- *System Design Interview — An Insider’s Guide*

**Talks / artykuły**

- High Scalability
- Martin Fowler — caching and resilience patterns

---

## Blok B — [Observability: dowody zamiast intuicji](./src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_b/README.md)

### Zakres

- Logi strukturalne:
  - JSON,
  - correlation ID,
  - request → downstream correlation.
- Metryki:
  - throughput,
  - latency,
  - errors,
  - p95,
  - p99,
  - saturation.
- Tracing:
  - OpenTelemetry,
  - API → cache → DB,
  - sampling,
  - trace context propagation.
- Runbooki:
  - latency spike,
  - DB down,
  - Redis down,
  - broker lag,
  - error rate spike.

### Kryteria ukończenia

Gdy ktoś mówi „system jest wolny”, pytasz:

> Która metryka to pokazuje?

I potrafisz przejść od metryki do logów, trace’ów i hipotezy technicznej.

### Materiały

**Książki**

- *Observability Engineering*
- *Site Reliability Engineering*

**Dokumentacja / kursy**

- OpenTelemetry docs
- Prometheus best practices
- Grafana dashboards & alerts

---

## Blok C — [Cloud: jeden provider, ale porządnie](./src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_c/README.md)

### Zakres

Wybierz jeden cloud provider, najlepiej AWS albo GCP, i poznaj go praktycznie.

- Compute:
  - autoscaling,
  - stateless workloads,
  - kontenery,
  - podstawy deployu.
- Storage:
  - managed database,
  - RDS lub odpowiednik,
  - read replicas,
  - backupy,
  - restore.
- Networking:
  - VPC,
  - public/private subnets,
  - security groups,
  - routing.
- Koszt vs wydajność:
  - główne cost drivers,
  - autoscaling vs overprovisioning,
  - cache jako narzędzie wydajności i oszczędności.

### Kryteria ukończenia

Potrafisz powiedzieć:

> To rozwiązanie jest technicznie poprawne, ale zbyt drogie. Zróbmy inaczej.

### Materiały

**Książki / przewodniki**

- AWS Well-Architected Framework
- *Cloud Native Patterns*

**Kursy**

- AWS Architecture Essentials
- GCP Architecture, jeśli wybierzesz Google Cloud

---

# Etap 4: Top Senior — dojrzałość techniczna i wpływ

Ten etap nie polega na dorzuceniu kolejnych bibliotek. Chodzi o jakość decyzji, przewidywanie awarii, wpływ na ludzi i rozumienie biznesu.

---

## 1. Myślenie systemowe

Top senior:

- przewiduje, jak system się zepsuje, zanim się zepsuje,
- rozumie failure modes,
- wskazuje najsłabszy punkt architektury,
- potrafi powiedzieć, przy jakim QPS system zacznie się degradować i dlaczego.

### Must-have tematy

- CAP theorem w praktyce.
- Partial failure.
- Network partition.
- Data inconsistency.
- Backpressure.
- Retry storm.
- Load shedding.

Jeśli nie umiesz wskazać najsłabszego punktu systemu, to jeszcze nie myślisz systemowo na poziomie top seniora.

---

## 2. Decyzje architektoniczne pod presją

Top senior:

- podejmuje nieidealne decyzje w dobrym momencie,
- zna koszt nadmiarowej abstrakcji,
- zna koszt przedwczesnej optymalizacji,
- potrafi powiedzieć „nie” PM-owi, managerowi i innemu seniorowi,
- odróżnia decyzje odwracalne od nieodwracalnych.

### Praktyki

- ADR jako standard.
- Jawne trade-offy.
- Dokumentowanie kontekstu decyzji.
- Rozdzielanie preferencji od ograniczeń systemowych.

---

## 3. Produkcja ważniejsza niż kod

Top senior:

- rozumie incidenty,
- wie, jak wygląda rollback,
- umie zrobić hotfix bez dokładania chaosu,
- rozumie data repair,
- pisze kod, który da się debugować.

### Obowiązkowe tematy

- SLO.
- SLA.
- Error budget.
- Postmortemy bez szukania winnych.
- Feature flags.
- Rollback strategy.
- Operational readiness.

Brak doświadczenia z realnymi incidentami produkcyjnymi jest istotną luką. Można ją częściowo zasymulować projektami i game dayami, ale nie warto udawać, że to to samo.

---

## 4. Głęboka specjalizacja i szerokie horyzonty

Top senior to T-shaped engineer.

### Głębokość

Wybierz obszar, w którym jesteś naprawdę mocny:

- Java / JVM,
- distributed backend,
- data-intensive systems,
- performance engineering,
- cloud architecture.

### Szerokość

Rozumiesz wystarczająco dużo o:

- frontend constraints,
- infrastrukturze,
- bezpieczeństwie,
- danych,
- kosztach,
- biznesie.

Bycie „od wszystkiego” bez głębi jest ryzykowne. Taka osoba bywa użyteczna operacyjnie, ale łatwo zastępowalna strategicznie.

---

## 5. Wpływ na ludzi

Top senior:

- podnosi poziom całego zespołu,
- robi code review jako lekcję, nie egzekucję,
- pomaga juniorom i midom rosnąć,
- tworzy standardy, które inni rozumieją i stosują,
- jest multiplierem, nie tylko najlepszym indywidualnym graczem.

### Sygnały

- Ludzie przychodzą po Twoją opinię.
- Twoje decyzje są cytowane.
- Dostajesz trudne problemy, nie tylko taski.
- Junior po kilku miesiącach z Tobą pisze lepszy kod.

---

## 6. Rozumienie biznesu

Top senior:

- rozumie, po co coś robimy,
- potrafi przeliczyć ryzyko techniczne na koszt,
- rozumie wpływ opóźnienia na biznes,
- nie gardzi feature’ami,
- umie rozmawiać z biznesem bez cynizmu.

Jeśli Twoim domyślnym poglądem jest „biznes psuje kod”, to prawdopodobnie problemem nie jest biznes, tylko niedojrzałe rozumienie roli inżyniera.

---

## 7. Reputacja i sygnały seniority

Bez widoczności możesz być bardzo dobry, ale niewidoczny.

### Sygnały top seniora

- Ludzie proszą Cię o review architektury.
- Dostajesz problemy z dużą niepewnością.
- Twoje decyzje są punktem odniesienia.
- Umiesz pisać i mówić o technicznych trade-offach.

### Opcjonalnie, ale pomocne

- Tech blog — jakość ważniejsza niż ilość.
- Prezentacje wewnętrzne.
- Udział w architecture review.
- Dokumentowanie ADR.
- Mentoring.

---

# Kolejność nauki system design

Rekomendowana kolejność projektów i ćwiczeń:

1. URL Shortener
2. Rate Limiter
3. Notification System
4. Chat System
5. News Feed
6. File Storage
7. E-commerce
8. Video Streaming
9. Ride Sharing
10. Payment System
11. Search Autocomplete
12. Metrics / Logging System

---

# Jak pracować z tą roadmapą

## Zasada 1: nie przechodź dalej bez artefaktów

Każdy większy blok powinien zostawić po sobie coś konkretnego:

- test,
- benchmark,
- diagram,
- ADR,
- dashboard,
- runbook,
- opis trade-offów,
- wynik profilingu,
- analizę `EXPLAIN ANALYZE`.

Sama przeczytana książka nie jest dowodem kompetencji.

## Zasada 2: projekty mają udowadniać decyzje

W każdym projekcie dokumentuj:

- jakie były opcje,
- dlaczego wybrałeś daną opcję,
- jaki jest koszt tej decyzji,
- kiedy decyzja przestanie być dobra,
- jak ją odwrócić.

## Zasada 3: testuj awarie, nie tylko happy path

Projekt senior-level powinien mieć scenariusze typu:

- broker niedostępny,
- DB wolna,
- Redis padł,
- event przyszedł dwa razy,
- request przekroczył timeout,
- retry spowodował przeciążenie,
- dane wymagają naprawy.

## Zasada 4: mierz, zanim optymalizujesz

Każda poważna optymalizacja powinna mieć:

- metrykę bazową,
- hipotezę,
- zmianę,
- pomiar po zmianie,
- wniosek.

Bez tego łatwo zrobić „optymalizację”, która tylko skomplikuje system.

---

# Minimalna definicja „done” dla całej roadmapy

Możesz uznać roadmapę za realnie przepracowaną, jeśli masz:

- minimum 3 solidne projekty techniczne,
- testy concurrency i testy negatywne security,
- przynajmniej jeden projekt z event-driven flow,
- przynajmniej jeden projekt z pełną obserwowalnością,
- load testy i wnioski,
- dashboardy i alerty,
- runbooki,
- ADR-y,
- opisane trade-offy architektoniczne,
- podstawowe koszty cloudowe,
- umiejętność obrony decyzji technicznych na rozmowie albo review.

---

# Najważniejszy filtr

Nie pytaj tylko: „czy znam tę technologię?”.

Pytaj:

- Czy wiem, kiedy jej użyć?
- Czy wiem, kiedy jej nie użyć?
- Czy umiem pokazać koszt tej decyzji?
- Czy umiem zdiagnozować problem, gdy coś pójdzie źle?
- Czy system da się utrzymać po moim odejściu?

To jest różnica między pisaniem kodu a dojrzałą inżynierią.
