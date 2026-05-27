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

- [`executor_service`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/executor_service/README.md)
  - `ExecutorService` i `ThreadPoolExecutor`,
  - pule wątków: fixed, cached, single-thread, scheduled,
  - kolejki zadań,
  - strategie odrzucania zadań,
  - lifecycle: `shutdown`, `shutdownNow`, `awaitTermination`,
  - dobór rozmiaru puli do pracy CPU-bound i IO-bound.

- [`fork_join`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/fork_join/README.md)
  - `ForkJoinPool`,
  - `RecursiveTask` i `RecursiveAction`,
  - work stealing,
  - kiedy Fork/Join ma sens,
  - kiedy pogarsza wydajność,
  - różnica między rekurencyjnym dzieleniem pracy a zwykłym async processingiem.

- [`completable_future`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/completable_future/README.md)
  - `CompletableFuture`,
  - fan-out / fan-in,
  - `thenApply`, `thenCompose`, `thenCombine`,
  - `allOf`, `anyOf`,
  - timeouty,
  - fallbacki,
  - obsługa błędów,
  - propagacja wyjątków.

- [`cancel`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/cancel)
  - anulowanie zadań,
  - `Future.cancel`,
  - przerwania wątków,
  - `InterruptedException`,
  - poprawna obsługa flagi przerwania,
  - różnica między anulowaniem a zatrzymaniem pracy.

- [`concurrent_hash_map`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/concurrent_hash_map/README.md)
  - `ConcurrentHashMap`,
  - atomowe operacje: `compute`, `computeIfAbsent`, `merge`,
  - problemy z operacjami typu check-then-act,
  - bezpieczna aktualizacja współdzielonego stanu,
  - różnice względem `HashMap` i `Collections.synchronizedMap`.

- [`race_condition`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/race_condition/README.md)
  - race condition,
  - lost update,
  - check-then-act,
  - read-modify-write,
  - overselling,
  - sposoby ochrony: synchronizacja, locki, typy atomowe, struktury concurrent.

- [`deadlock`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/deadlock/README.md)
  - deadlock,
  - warunki konieczne zakleszczenia,
  - przykład z wieloma lockami,
  - unikanie deadlocków przez kolejność blokad,
  - timeouty przy próbie zdobycia locka,
  - diagnostyka deadlocków.

- [`visibility`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/visibility/README.md)
  - problem widoczności zmian między wątkami,
  - `volatile`,
  - happens-before,
  - publikacja obiektów,
  - różnica między atomicity a visibility,
  - bezpieczne kończenie pracy wątku przez flagę.

- [`thread_confinement`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/thread_confinement/README.md)
  - thread confinement,
  - stack confinement,
  - `ThreadLocal`,
  - unikanie współdzielonego stanu,
  - kiedy izolacja danych jest lepsza niż synchronizacja,
  - typowe błędy przy użyciu `ThreadLocal`.

- [`testing`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/testing/README.md)
  - testowanie kodu współbieżnego,
  - problemy z niedeterministycznością,
  - testy race condition,
  - użycie `CountDownLatch`, `CyclicBarrier`, `ExecutorService`,
  - testowanie timeoutów i błędów,
  - unikanie testów opartych wyłącznie o `Thread.sleep`.

### Kryteria ukończenia

- Potrafisz zreprodukować race condition w teście i wyjaśnić, dlaczego wynik jest niedeterministyczny.
- Potrafisz celowo wywołać deadlock, zdiagnozować jego przyczynę i naprawić problem.
- Potrafisz rozpoznać lost update, visibility problem i błędne użycie `volatile`.
- Potrafisz dobrać odpowiedni mechanizm: `synchronized`, lock, typ atomowy, kolekcję concurrent albo izolację stanu.
- Potrafisz użyć `ExecutorService` bez wycieków wątków i poprawnie zakończyć pulę.
- Potrafisz obsłużyć anulowanie, timeout i wyjątek w `CompletableFuture`.
- Potrafisz dobrać model równoległości do problemu, zamiast używać async „bo wygląda nowocześnie”.

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

- [`heap_vs_stack`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/heap_vs_stack/README.md)
  - różnica między heapem a stackiem,
  - gdzie trafiają obiekty, referencje i zmienne lokalne,
  - koszt alokacji obiektów,
  - kiedy alokacje zaczynają być problemem.

- [`heap_size`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/heap_size/README.md)
  - wpływ rozmiaru heapu na aplikację,
  - `-Xms` i `-Xmx`,
  - zbyt mały vs zbyt duży heap,
  - objawy presji na pamięć,
  - relacja między heapem, GC i latency.

- [`g1_vs_zgc`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/g1_vs_zgc/README.md)
  - G1 jako rozsądny default,
  - ZGC dla dużych heapów i niskich pauz,
  - pause time vs throughput,
  - kiedy ZGC jest przerostem formy,
  - podstawowe różnice w zachowaniu GC.

- [`allocation_rate`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/allocation_rate/README.md)
  - allocation rate,
  - allocation churn,
  - krótkotrwałe obiekty,
  - wpływ alokacji na GC,
  - rozpoznawanie nadmiernych alokacji w profilerze.

- [`escape_analysis`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/escape_analysis/README.md)
  - escape analysis,
  - scalar replacement,
  - stack allocation jako optymalizacja JVM,
  - kiedy obiekt może zostać zoptymalizowany,
  - dlaczego kod źródłowy nie zawsze mówi całą prawdę o koszcie alokacji.

- [`cpu_vs_io`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/cpu_vs_io/README.md)
  - różnica między pracą CPU-bound i IO-bound,
  - objawy ograniczenia przez CPU,
  - objawy oczekiwania na IO,
  - wpływ typu pracy na dobór liczby wątków,
  - dlaczego więcej wątków nie zawsze przyspiesza aplikację.

- [`thread_count`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/thread_count/README.md)
  - koszt tworzenia i utrzymywania wątków,
  - context switching,
  - oversubscription,
  - wpływ liczby wątków na latency i throughput,
  - dobór liczby wątków do typu obciążenia.

- [`lock_contention`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/lock_contention/README.md)
  - lock contention,
  - wątki blokujące się na tych samych zasobach,
  - wpływ locków na throughput,
  - rozpoznawanie contention w profilerze,
  - strategie ograniczania współdzielonego stanu.

- [`false_sharing`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/false_sharing/README.md)
  - false sharing,
  - cache line,
  - degradacja wydajności bez logicznego konfliktu danych,
  - kiedy problem może wystąpić,
  - jak go rozpoznać i ograniczyć.

- [`stream_vs_loop`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/stream_vs_loop/README.md)
  - różnice między streamem a pętlą,
  - koszt abstrakcji,
  - kiedy stream poprawia czytelność,
  - kiedy zwykła pętla jest lepsza,
  - dlaczego mikrooptymalizacje wymagają pomiaru.

- [`array_vs_linked`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/array_vs_linked/README.md)
  - lokalność pamięci,
  - cache friendliness,
  - `ArrayList` vs `LinkedList`,
  - koszt przechodzenia po strukturach danych,
  - wpływ struktury danych na wydajność CPU.

- [`big_decimal`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/big_decimal/README.md)
  - koszt `BigDecimal`,
  - precyzja vs wydajność,
  - tworzenie wielu obiektów pośrednich,
  - typowe błędy w kodzie finansowym,
  - kiedy `long` z jednostką minimalną może być lepszy.

- [`object_pooling`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/object_pooling/README.md)
  - object pooling,
  - kiedy miał sens historycznie,
  - kiedy pogarsza sytuację,
  - wpływ poolingu na GC i złożoność kodu,
  - różnica między poolingiem obiektów a poolingiem zasobów.

- [`polymorphism_vs_jit`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/polymorphism_vs_jit/README.md)
  - wpływ polimorfizmu na JIT,
  - inline caching,
  - monomorphic, bimorphic i megamorphic call sites,
  - kiedy wirtualne wywołania są tanie,
  - kiedy utrudniają optymalizacje.

- [`naive_jmh`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/naive_jmh/README.md)
  - podstawy JMH,
  - warm-up,
  - dead code elimination,
  - constant folding,
  - typowe błędy w mikrobenchmarkach,
  - dlaczego `System.nanoTime()` zwykle nie wystarcza.

### Kryteria ukończenia

- Potrafisz odpowiedzieć „dlaczego to jest wolne?” na podstawie profilu, metryk lub benchmarku.
- Umiesz odróżnić problem CPU, IO, GC, alokacji, locków i bazy danych.
- Potrafisz pokazać hotspot CPU lub alokacji i wyciągnąć z niego konkretny wniosek.
- Rozumiesz wpływ heapu, liczby wątków i allocation rate na latency oraz throughput.
- Potrafisz wykonać prosty benchmark w JMH bez najczęstszych błędów.
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

- [`bean`](./bean)
  - lifecycle beanów,
  - kiedy powstaje bean,
  - dependency injection,
  - singleton scope,
  - inicjalizacja i niszczenie beanów,
  - wpływ kolejności tworzenia beanów na aplikację.

- [`configuration`](./configuration)
  - `@Configuration`,
  - `@Bean`,
  - component scanning,
  - auto-configuration,
  - properties i profile,
  - różnica między konfiguracją jawną a automatyczną.

- [`aspect`](./aspect)
  - Spring AOP,
  - proxy,
  - proxy boundaries,
  - kiedy powstaje proxy,
  - co przechodzi przez proxy, a co je omija,
  - wpływ AOP na `@Transactional`, security i logowanie.

- [`transactional`](./transactional)
  - `@Transactional`,
  - `REQUIRED`, `REQUIRES_NEW`, `NESTED`,
  - isolation levels,
  - rollback rules,
  - self-invocation,
  - lazy loading i granice transakcji,
  - typowe przypadki, w których transakcja nie działa.

- [`jpa`](./jpa)
  - persistence context,
  - dirty checking,
  - lazy vs eager loading,
  - problem N+1,
  - flush vs commit,
  - transakcje w kontekście JPA,
  - typowe błędy przy pracy z encjami.

- [`mvc`](./mvc)
  - Spring MVC request lifecycle,
  - controller, service, repository,
  - `DispatcherServlet`,
  - binding requestów,
  - walidacja wejścia,
  - mapowanie odpowiedzi HTTP.

- [`exception`](./exception)
  - globalny exception handling,
  - `@ControllerAdvice`,
  - mapowanie wyjątków na statusy HTTP,
  - spójny error contract,
  - brak wycieków informacji w komunikatach błędów,
  - rozróżnienie błędów domenowych, walidacyjnych i technicznych.

- [`authorization`](./authorization)
  - różnica między authentication i authorization,
  - JWT access token i refresh token,
  - rotacja refresh tokenów,
  - OAuth2 basics,
  - resource server,
  - authorization server w minimalnym zakresie,
  - `@PreAuthorize` i SpEL,
  - autoryzacja oparta o dane: właściciel, manager, HR, organizacja, tenant.

- [`test`](./test)
  - testowanie Springa bez ładowania całego kontekstu,
  - testy warstwowe,
  - `@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`,
  - testowanie transakcji,
  - testowanie security,
  - mockowanie zależności,
  - kiedy test integracyjny ma sens.

### Kryteria ukończenia

- Potrafisz pokazać przypadek, w którym `@Transactional` nie działa, i wyjaśnić dlaczego.
- Rozumiesz proxy boundaries i umiesz przewidzieć, które adnotacje Springa zostaną faktycznie zastosowane.
- Potrafisz wyjaśnić lifecycle beana oraz moment powstania proxy.
- Potrafisz rozpoznać problem z JPA: N+1, lazy loading, flush, dirty checking albo brak transakcji.
- Potrafisz zaprojektować spójny error contract bez ujawniania szczegółów technicznych.
- Rozumiesz, że autoryzacja nie kończy się na rolach.
- Potrafisz zaimplementować dostęp zależny od danych: właściciel, manager, HR, organizacja, tenant.
- Potrafisz dobrać typ testu Springa do problemu zamiast zawsze używać `@SpringBootTest`.

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

- #### SQL

  - [`sql/index`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/index/README.md)
      - indeksy B-tree,
        - kiedy indeks pomaga,
        - kiedy indeks szkodzi,
        - koszt zapisu vs koszt odczytu,
        - selectivity,
        - composite indexes,
        - kolejność kolumn w indeksie.
  
  - [`sql/execution_plan`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/execution_plan/README.md)
      - `EXPLAIN` i `EXPLAIN ANALYZE`,
        - sequential scan,
        - index scan,
        - bitmap index scan,
        - nested loop,
        - hash join,
        - sort,
        - koszt estymowany vs rzeczywisty,
        - odczyt planu wykonania bez zgadywania.
  
  - [`sql/transaction`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/transaction/README.md)
      - transakcje SQL,
        - `READ_COMMITTED`,
        - `REPEATABLE_READ`,
        - phantom reads,
        - atomicity i consistency,
        - spójność vs throughput,
        - dobór isolation level do problemu.
  
  - [`sql/lock`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/lock/README.md)
      - optimistic locking,
        - pessimistic locking,
        - row-level locks,
        - lock wait,
        - deadlock w bazie danych,
        - wpływ locków na przepustowość,
        - kiedy blokowanie jest poprawnym trade-offem.
  
  - [`sql/n_plus_one`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/n_plus_one/README.md)
      - problem N+1,
        - rozpoznawanie po liczbie zapytań,
        - `fetch join`,
        - entity graph,
        - batch fetching,
        - kiedy naprawa N+1 nie jest najlepszym trade-offem.
  
  - [`sql/pagination`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/pagination/README.md)
      - offset pagination,
        - keyset pagination,
        - koszt dużego offsetu,
        - stabilne sortowanie,
        - paginacja po indeksie,
        - konsekwencje dla API.
  
  - [`sql/scale`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/scale/README.md)
      - read replicas,
        - sharding,
        - partitioning,
        - connection pool,
        - cache przy bazie danych,
        - ograniczenia skalowania pionowego i poziomego.
  
  - [`sql/workload`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/workload/README.md)
      - workload OLTP vs OLAP,
        - read-heavy vs write-heavy,
        - latency vs throughput,
        - wpływ wzorca zapytań na indeksy,
        - projektowanie pod realne obciążenie.

- #### NoSQL

  - [`nosql/modeling`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/modeling/README.md)
      - modelowanie danych pod zapytania,
        - denormalizacja,
        - aggregate boundary,
        - brak joinów jako decyzja projektowa,
        - różnica między modelem relacyjnym a dokumentowym.
  
  - [`nosql/document`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/document/README.md)
      - bazy dokumentowe,
        - dokument jako agregat,
        - osadzanie vs referencje,
        - aktualizacja części dokumentu,
        - ryzyko zbyt dużych dokumentów.
  
  - [`nosql/key_value`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/key_value/README.md)
      - key-value store,
        - szybki odczyt po kluczu,
        - cache,
        - TTL,
        - ograniczenia zapytań,
        - kiedy prosty model jest zaletą.
  
  - [`nosql/wide_column`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/wide_column/README.md)
      - wide-column stores,
        - partition key,
        - clustering key,
        - projektowanie pod access pattern,
        - unikanie hot partitions,
        - ograniczenia zapytań ad hoc.
  
  - [`nosql/graph`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/graph/README.md)
      - bazy grafowe,
        - relacje jako pierwszy obywatel modelu,
        - traversale,
        - kiedy graf ma sens,
        - kiedy relacyjna baza wystarczy.
  
  - [`nosql/consistency`](./src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/consistency/README.md)
      - eventual consistency,
        - strong consistency,
        - consistency vs availability,
        - read-your-writes,
        - conflict resolution,
      - konsekwencje spójności dla UX i logiki biznesowej.

### Kryteria ukończenia

### Kryteria ukończenia

- Potrafisz dobrać indeks do zapytania i uzasadnić koszt zapisu oraz odczytu.
- Umiesz poprawić zapytanie lub indeks na podstawie `EXPLAIN ANALYZE`.
- Potrafisz dobrać isolation level i locking do konkretnego problemu biznesowego.
- Potrafisz rozpoznać N+1 i wybrać sensowną strategię naprawy.
- Rozumiesz różnicę między offset pagination a keyset pagination.
- Potrafisz dobrać typ bazy NoSQL do access patternu, a nie do mody.
- Potrafisz uzasadnić kompromis między spójnością, dostępnością i przepustowością.

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

- [`monolith_vs_microservices`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/monolith_vs_microservices/README.md)
  - monolit,
  - modular monolith,
  - mikroserwisy,
  - koszty operacyjne, poznawcze i organizacyjne,
  - granice, przy których monolit przestaje wystarczać,
  - problemy sieci, latency i eventual consistency.

- [`ddd_strategy`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/ddd_strategy/README.md)
  - DDD strategiczne,
  - bounded context,
  - ubiquitous language,
  - context map,
  - ownership,
  - relacje między zespołami a granicami systemu.

- [`ddd_tactic`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/ddd_tactic/README.md)
  - DDD taktyczne,
  - aggregate,
  - entity,
  - value object,
  - invarianty,
  - domain service,
  - granice transakcji.

- [`clean_architecture`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/clean_architecture/README.md)
  - Hexagonal Architecture,
  - Clean Architecture,
  - domena bez Springa,
  - use case’y w warstwie application,
  - porty i adaptery,
  - framework jako szczegół implementacyjny.

- [`use_case`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/use_case/README.md)
  - modelowanie przypadków użycia,
  - application service,
  - input/output port,
  - orkiestracja logiki,
  - oddzielenie logiki aplikacyjnej od domenowej,
  - granice odpowiedzialności use case’a.

- [`integration`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/integration/README.md)
  - integracja między modułami i systemami,
  - API synchroniczne vs asynchroniczne,
  - kontrakty integracyjne,
  - zdarzenia domenowe,
  - eventual consistency,
  - odporność na błędy integracji.

### Kryteria ukończenia

- Potrafisz wskazać konkretne punkty bólu, przy których modular monolith przestaje wystarczać.
- Umiesz uzasadnić wybór monolitu, modular monolith albo mikroserwisów bez modnych haseł.
- Potrafisz wyznaczyć bounded context na podstawie domeny, ownershipu i języka biznesowego.
- Rozumiesz różnicę między agregatem, encją, value objectem i use case’em.
- Potrafisz zaprojektować domenę bez zależności od Springa.
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

- [`kafka`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/kafka/README.md)
  - podstawy wybranego brokera,
  - topic, partition, offset,
  - consumer group,
  - ordering w ramach partycji,
  - commit offsetu,
  - delivery semantics,
  - ograniczenia exactly-once.

- [`consumer`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/consumer/README.md)
  - projektowanie konsumenta,
  - side effects,
  - transakcyjność operacji po stronie konsumenta,
  - bezpieczne ponawianie,
  - obsługa błędów w połowie operacji,
  - kiedy commitować offset.

- [`domain`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/domain/README.md)
  - event-driven flow w domenie,
  - event jako fakt biznesowy,
  - stany przejściowe, np. `PENDING`,
  - eventual consistency,
  - kompensacje,
  - obsługa opóźnionych eventów.

- [`versioning`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/versioning/README.md)
  - kontrakty eventów,
  - wersjonowanie schematów,
  - backward compatibility,
  - forward compatibility,
  - dodawanie i usuwanie pól,
  - ewolucja eventów bez psucia konsumentów.

- [`observability`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/observability/README.md)
  - logowanie przepływu eventów,
  - correlation ID,
  - metryki konsumentów,
  - lag konsumenta,
  - retry, backoff i jitter,
  - DLQ,
  - replay,
  - diagnozowanie problemów w przepływie asynchronicznym.

### Kryteria ukończenia

- Potrafisz zaprojektować konsumenta odpornego na duplikaty eventów.
- Potrafisz bezpiecznie obsłużyć sytuację, w której konsument pada w połowie operacji.
- Rozumiesz, dlaczego exactly-once nie powinno być głównym celem projektu.
- Potrafisz zastosować retry, backoff, jitter, DLQ i replay bez psucia danych.
- Potrafisz zaprojektować eventy tak, żeby dało się je wersjonować.
- Potrafisz pracować ze stanami przejściowymi i eventual consistency.
- System nie psuje danych, gdy event przyjdzie dwa razy, retry odpali się równolegle albo event dotrze z opóźnieniem.

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

- [`docker`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/docker/README.md)
  - Dockerfile,
  - multi-stage build,
  - małe obrazy,
  - szybkie buildy,
  - `.dockerignore`,
  - uruchamianie aplikacji z konfiguracją przez zmienne środowiskowe.

- [`kubernetes`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/kubernetes/README.md)
  - Pod,
  - Deployment,
  - Service,
  - ConfigMap,
  - Secret,
  - readiness probe,
  - liveness probe,
  - restart policy,
  - rolling update.

- [`configuration`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/configuration/README.md)
  - konfiguracja aplikacji per środowisko,
  - zmienne środowiskowe,
  - profile aplikacji,
  - ConfigMap vs Secret,
  - konfiguracja obrazu vs konfiguracja runtime,
  - typowe błędy przy przenoszeniu aplikacji z local na cluster.

- [`workshop`](./src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/workshop/README.md)
  - lokalny klaster przez kind albo minikube,
  - build obrazu,
  - push albo load obrazu do klastra,
  - `kubectl apply`,
  - sprawdzanie statusu poda,
  - logi, eventy i opis zasobów,
  - prosty pipeline: build, test, build image, opcjonalny deploy.

### Kryteria ukończenia

- Potrafisz zbudować obraz aplikacji i uruchomić go lokalnie.
- Potrafisz wdrożyć aplikację na lokalny klaster przez kind albo minikube.
- Rozumiesz, co dzieje się po `kubectl apply`.
- Potrafisz sprawdzić problem przez `kubectl logs`, `describe`, eventy i status zasobów.
- Wiesz, gdzie szukać przyczyny, gdy aplikacja działa lokalnie, ale nie działa na klastrze.
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

- [`concepts`](./src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/concepts/README.md)
  - skalowanie pionowe vs poziome,
  - stateless API,
  - bottlenecki przy wzroście RPS,
  - throughput vs latency,
  - capacity planning,
  - podstawowe założenia do projektowania systemu.

- [`metrics`](./src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/metrics/README.md)
  - RPS,
  - latency p50, p95, p99,
  - error rate,
  - saturation,
  - CPU, memory, network, disk IO,
  - queue depth,
  - metryki cache, bazy danych i zewnętrznych integracji.

- [`implementation`](./src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/implementation/README.md)
  - autoscaling,
  - caching z Redisem,
  - TTL, eviction, LRU vs LFU,
  - cache stampede,
  - single-flight,
  - jitter,
  - rate limiting: per IP, per API key, token bucket, sliding window,
  - circuit breaker,
  - retry, backoff i graceful degradation.

### Kryteria ukończenia

- Potrafisz narysować system i wskazać jego główne bottlenecki.
- Potrafisz oszacować, który element padnie przy określonym RPS i dlaczego.
- Umiesz dobrać cache, rate limiting, autoscaling albo circuit breaker do konkretnego problemu.
- Rozumiesz różnicę między poprawą throughputu a obniżeniem latency.
- Potrafisz uzasadnić decyzje na podstawie założeń, metryk i ograniczeń systemu.

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

- [`structured_logs`](./src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_b/structured_logs/README.md)
  - logi strukturalne w JSON,
  - correlation ID,
  - request ID,
  - request → downstream correlation,
  - poziomy logowania,
  - logowanie błędów bez utraty kontekstu.

- [`prometheus`](./src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_b/prometheus/README.md)
  - metryki aplikacyjne i systemowe,
  - throughput,
  - latency,
  - error rate,
  - p95 i p99,
  - saturation,
  - podstawy Prometheus i PromQL.

- [`tracing`](./src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_b/tracing/README.md)
  - distributed tracing,
  - OpenTelemetry,
  - trace ID i span ID,
  - API → cache → DB,
  - sampling,
  - trace context propagation,
  - szukanie źródła latency w zależnościach downstream.

- [`alerts`](./src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_b/alerts/README.md)
  - alerty oparte o symptomy, nie same przyczyny,
  - alerty dla latency, error rate i saturation,
  - progi alertów,
  - unikanie alert fatigue,
  - runbooki dla typowych awarii,
  - reakcja na DB down, Redis down, broker lag i error rate spike.

### Kryteria ukończenia

- Potrafisz wskazać metrykę, która potwierdza problem z systemem.
- Potrafisz przejść od metryki do logów, trace’ów i technicznej hipotezy.
- Umiesz użyć correlation ID do prześledzenia requestu przez zależności downstream.
- Potrafisz rozróżnić problem latency, error rate, saturation i dependency failure.
- Potrafisz zaprojektować alert, który opisuje realny objaw problemu.
- Masz runbook dla typowych awarii: DB down, Redis down, broker lag i spike błędów.

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

- Potrafisz wdrożyć prostą aplikację u wybranego cloud providera.
- Rozumiesz różnicę między compute, storage i networkingiem w praktycznym deployu.
- Potrafisz skonfigurować stateless workload z autoscalingiem.
- Potrafisz wyjaśnić podstawy VPC, subnetów, routingu i security groups.
- Potrafisz uruchomić managed database z backupem, restore i opcjonalną read replicą.
- Potrafisz wskazać główne cost drivers danego rozwiązania.
- Potrafisz odróżnić overprovisioning od sensownego autoscalingu.
- Potrafisz powiedzieć: „To rozwiązanie jest technicznie poprawne, ale zbyt drogie. Zróbmy inaczej.”

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
