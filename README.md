# Roadmapa rozwoju: Java Backend Mid → Senior → Top Senior

Ta roadmapa prowadzi od solidnego poziomu Mid do Seniora i dalej w stronę dojrzałego inżyniera backendowego. Nie chodzi o „zaliczenie technologii”, tylko o zbudowanie umiejętności podejmowania świadomych decyzji technicznych, projektowania systemów, diagnozowania problemów produkcyjnych i pracy z realnymi ograniczeniami biznesowymi.

Etapy porządkują kontekst problemu, a nie tempo nauki ani status zaliczenia.
Aktualną mapę laboratoriów i przekrojowy indeks pojęć zawiera [kompendium backend-engineering](backend-engineering/README.md).

## Założenia

- Stack główny: **Java, Spring, relacyjne bazy danych, systemy rozproszone**.
- Projekty są ważniejsze niż sama teoria.
- Zagadnienia warto zestawiać z artefaktami: README, testami, wynikami load testów, diagramami, decyzjami architektonicznymi i wnioskami.
- Seniority nie oznacza znajomości większej liczby frameworków, tylko lepsze decyzje, lepszą diagnostykę i większy wpływ na system oraz zespół.

## Mapa repozytorium

- [`backend-engineering/`](backend-engineering/README.md) — wykonywalne laboratoria i kompendium wiedzy dla etapów 1–3;
- [`projects/`](projects/README.md) — trzy projekty portfolio stosujące wiedzę z odpowiadających im etapów;
- [`system-design/`](system-design/README.md) — High Level Design: architektura, skalowanie i systemy rozproszone;
- [`low-level-design/`](low-level-design/README.md) — Low Level Design w Javie: klasy, odpowiedzialności, reguły domenowe i współpraca obiektów.

### Low Level Design — projektowanie w czystej Javie

[Ścieżka LLD](low-level-design/README.md) obejmuje 12 tematów technicznych
i osobną rozgrzewkę. To projekty edukacyjne skupione na strukturach danych,
algorytmach, niezmiennikach oraz kosztach czasu, pamięci i aktualizacji indeksów.

Każdy moduł ma własny build, testy, polskie komentarze i samodzielny README
z opisem działania, rdzenia technicznego, uruchomienia oraz ograniczeń.
README wyjaśnia wybór struktur i przebieg algorytmów na konkretnych przykładach;
komentarze uzupełniają to rozumowanie szczegółami kodu.
Główny nacisk pada na kolekcje JDK, referencje i kopie, pamięć, alokacje
oraz kontrakt wątkowy; reguły domeny służą sprawdzaniu poprawności tych mechanizmów.
Obowiązują [standard edukacyjny](low-level-design/EDUCATIONAL_STANDARD.md),
[procedura](low-level-design/PROCEDURE.md) i [zasady pracy](low-level-design/AGENTS.md).

---

# Etap 1 — fundamenty backend engineering

## Cel etapu

Ten obszar pomaga rozwijać umiejętności:

- rozumieć i kontrolować wydajność aplikacji na poziomie CPU, JVM, GC i DB,
- pisać kod odporny na konkurencję, race condition i błędy transakcyjne,
- traktować Springa jako narzędzie, a nie magię,
- pisać kod testowalny i możliwy do refaktoryzacji bez przepisywania systemu od zera.

---

## Blok A — [Java Concurrency](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/README.md)

Pełny blok obejmuje także [virtual threads](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/virtual_threads/README.md),
[Reactive Streams](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/reactive_streams/README.md) oraz
[czas, DST i schedulery](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/temporal_correctness/README.md).
Te laboratoria łączą model wykonania z ograniczonym downstreamem, demand/cancel
i jednoznaczną interpretacją momentu wykonania.


### Zakres

- [`executor_service`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/executor_service/README.md)
  - `ExecutorService` i `ThreadPoolExecutor`,
  - pule wątków: fixed, cached, single-thread, scheduled,
  - kolejki zadań,
  - strategie odrzucania zadań,
  - lifecycle: `shutdown`, `shutdownNow`, `awaitTermination`,
  - dobór rozmiaru puli do pracy CPU-bound i IO-bound.

- [`fork_join`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/fork_join/README.md)
  - `ForkJoinPool`,
  - `RecursiveTask` i `RecursiveAction`,
  - work stealing,
  - kiedy Fork/Join ma sens,
  - kiedy pogarsza wydajność,
  - różnica między rekurencyjnym dzieleniem pracy a zwykłym async processingiem.

- [`completable_future`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/completable_future/README.md)
  - `CompletableFuture`,
  - fan-out / fan-in,
  - `thenApply`, `thenCompose`, `thenCombine`,
  - `allOf`, `anyOf`,
  - timeouty,
  - fallbacki,
  - obsługa błędów,
  - propagacja wyjątków.

- [`cancel`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/cancel)
  - anulowanie zadań,
  - `Future.cancel`,
  - przerwania wątków,
  - `InterruptedException`,
  - poprawna obsługa flagi przerwania,
  - różnica między anulowaniem a zatrzymaniem pracy.

- [`concurrent_hash_map`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/concurrent_hash_map/README.md)
  - `ConcurrentHashMap`,
  - atomowe operacje: `compute`, `computeIfAbsent`, `merge`,
  - problemy z operacjami typu check-then-act,
  - bezpieczna aktualizacja współdzielonego stanu,
  - różnice względem `HashMap` i `Collections.synchronizedMap`.

- [`race_condition`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/race_condition/README.md)
  - race condition,
  - lost update,
  - check-then-act,
  - read-modify-write,
  - overselling,
  - sposoby ochrony: synchronizacja, locki, typy atomowe, struktury concurrent.

- [`deadlock`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/deadlock/README.md)
  - deadlock,
  - warunki konieczne zakleszczenia,
  - przykład z wieloma lockami,
  - unikanie deadlocków przez kolejność blokad,
  - timeouty przy próbie zdobycia locka,
  - diagnostyka deadlocków.

- [`visibility`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/visibility/README.md)
  - problem widoczności zmian między wątkami,
  - `volatile`,
  - happens-before,
  - publikacja obiektów,
  - różnica między atomicity a visibility,
  - bezpieczne kończenie pracy wątku przez flagę.

- [`thread_confinement`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/thread_confinement/README.md)
  - thread confinement,
  - stack confinement,
  - `ThreadLocal`,
  - unikanie współdzielonego stanu,
  - kiedy izolacja danych jest lepsza niż synchronizacja,
  - typowe błędy przy użyciu `ThreadLocal`.

- [`testing`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/testing/README.md)
  - testowanie kodu współbieżnego,
  - problemy z niedeterministycznością,
  - testy race condition,
  - użycie `CountDownLatch`, `CyclicBarrier`, `ExecutorService`,
  - testowanie timeoutów i błędów,
  - unikanie testów opartych wyłącznie o `Thread.sleep`.

### Pytania sprawdzające rozumienie

- Czy potrafisz zreprodukować race condition w teście i wyjaśnić, dlaczego wynik jest niedeterministyczny?
- Czy potrafisz celowo wywołać deadlock, zdiagnozować jego przyczynę i naprawić problem?
- Czy potrafisz rozpoznać lost update, visibility problem i błędne użycie `volatile`?
- Czy potrafisz dobrać odpowiedni mechanizm: `synchronized`, lock, typ atomowy, kolekcję concurrent albo izolację stanu?
- Czy potrafisz użyć `ExecutorService` bez wycieków wątków i poprawnie zakończyć pulę?
- Czy potrafisz obsłużyć anulowanie, timeout i wyjątek w `CompletableFuture`?
- Czy potrafisz dobrać model równoległości do problemu, zamiast używać async „bo wygląda nowocześnie”?

### Materiały

**Książki**

- *Java Concurrency in Practice*
- *Effective Java*

**Kursy / materiały wideo**

- Pluralsight — Java Concurrency
- Materiały Briana Goetza i José Paumarda

---

## Blok B — [JVM i profilowanie](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/README.md)

### Zakres

- [`heap_vs_stack`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/heap_vs_stack/README.md)
  - różnica między heapem a stackiem,
  - gdzie trafiają obiekty, referencje i zmienne lokalne,
  - koszt alokacji obiektów,
  - kiedy alokacje zaczynają być problemem.

- [`heap_size`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/heap_size/README.md)
  - wpływ rozmiaru heapu na aplikację,
  - `-Xms` i `-Xmx`,
  - zbyt mały vs zbyt duży heap,
  - objawy presji na pamięć,
  - relacja między heapem, GC i latency.

- [`g1_vs_zgc`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/g1_vs_zgc/README.md)
  - G1 jako rozsądny default,
  - ZGC dla dużych heapów i niskich pauz,
  - pause time vs throughput,
  - kiedy ZGC jest przerostem formy,
  - podstawowe różnice w zachowaniu GC.

- [`allocation_rate`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/allocation_rate/README.md)
  - allocation rate,
  - allocation churn,
  - krótkotrwałe obiekty,
  - wpływ alokacji na GC,
  - rozpoznawanie nadmiernych alokacji w profilerze.

- [`escape_analysis`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/escape_analysis/README.md)
  - escape analysis,
  - scalar replacement,
  - eliminacja alokacji i scalar replacement — bez zakładania alokacji obiektu na stosie,
  - kiedy obiekt może zostać zoptymalizowany,
  - dlaczego kod źródłowy nie zawsze mówi całą prawdę o koszcie alokacji.

- [`cpu_vs_io`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/cpu_vs_io/README.md)
  - różnica między pracą CPU-bound i IO-bound,
  - objawy ograniczenia przez CPU,
  - objawy oczekiwania na IO,
  - wpływ typu pracy na dobór liczby wątków,
  - dlaczego więcej wątków nie zawsze przyspiesza aplikację.

- [`thread_count`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/thread_count/README.md)
  - koszt tworzenia i utrzymywania wątków,
  - context switching,
  - oversubscription,
  - wpływ liczby wątków na latency i throughput,
  - dobór liczby wątków do typu obciążenia.

- [`lock_contention`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/lock_contention/README.md)
  - lock contention,
  - wątki blokujące się na tych samych zasobach,
  - wpływ locków na throughput,
  - rozpoznawanie contention w profilerze,
  - strategie ograniczania współdzielonego stanu.

- [`false_sharing`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/false_sharing/README.md)
  - false sharing,
  - cache line,
  - degradacja wydajności bez logicznego konfliktu danych,
  - kiedy problem może wystąpić,
  - jak go rozpoznać i ograniczyć.

- [`stream_vs_loop`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/stream_vs_loop/README.md)
  - różnice między streamem a pętlą,
  - koszt abstrakcji,
  - kiedy stream poprawia czytelność,
  - kiedy zwykła pętla jest lepsza,
  - dlaczego mikrooptymalizacje wymagają pomiaru.

- [`array_vs_linked`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/array_vs_linked/README.md)
  - lokalność pamięci,
  - cache friendliness,
  - `ArrayList` vs `LinkedList`,
  - koszt przechodzenia po strukturach danych,
  - wpływ struktury danych na wydajność CPU.

- [`big_decimal`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/big_decimal/README.md)
  - koszt `BigDecimal`,
  - precyzja vs wydajność,
  - tworzenie wielu obiektów pośrednich,
  - typowe błędy w kodzie finansowym,
  - kiedy `long` z jednostką minimalną może być lepszy.

- [`object_pooling`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/object_pooling/README.md)
  - object pooling,
  - kiedy miał sens historycznie,
  - kiedy pogarsza sytuację,
  - wpływ poolingu na GC i złożoność kodu,
  - różnica między poolingiem obiektów a poolingiem zasobów.

- [`polymorphism_vs_jit`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/polymorphism_vs_jit/README.md)
  - wpływ polimorfizmu na JIT,
  - inline caching,
  - monomorphic, bimorphic i megamorphic call sites,
  - kiedy wirtualne wywołania są tanie,
  - kiedy utrudniają optymalizacje.

- [`naive_vs_jmh`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/naive_vs_jmh/README.md)
  - podstawy JMH,
  - warm-up,
  - dead code elimination,
  - constant folding,
  - typowe błędy w mikrobenchmarkach,
  - dlaczego `System.nanoTime()` zwykle nie wystarcza.

### Pytania sprawdzające rozumienie

- Czy potrafisz odpowiedzieć „dlaczego to jest wolne?” na podstawie profilu, metryk lub benchmarku?
- Czy umiesz odróżnić problem CPU, IO, GC, alokacji, locków i bazy danych?
- Czy potrafisz pokazać hotspot CPU lub alokacji i wyciągnąć z niego konkretny wniosek?
- Czy rozumiesz wpływ heapu, liczby wątków i allocation rate na latency oraz throughput?
- Czy potrafisz wykonać prosty benchmark w JMH bez najczęstszych błędów?
- Czy nie diagnozujesz wydajności wyłącznie intuicją?

### Materiały

**Książki**

- *Java Performance*
- *Optimizing Java*

**Kursy / talks**

- Oracle JFR & JVM Internals talks
- JetBrains JVM Profiling videos

---

## Blok C — [Spring pod maską](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/README.md)

### Zakres

- [`bean`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/bean/README.md)
  - lifecycle beanów,
  - kiedy powstaje bean,
  - dependency injection,
  - singleton scope,
  - inicjalizacja i niszczenie beanów,
  - wpływ kolejności tworzenia beanów na aplikację.

- [`configuration`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/configuration/README.md)
  - `@Configuration`,
  - `@Bean`,
  - component scanning,
  - auto-configuration,
  - properties i profile,
  - różnica między konfiguracją jawną a automatyczną.

- [`aspect`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/aspect/README.md)
  - Spring AOP,
  - proxy,
  - proxy boundaries,
  - kiedy powstaje proxy,
  - co przechodzi przez proxy, a co je omija,
  - wpływ AOP na `@Transactional`, security i logowanie.

- [`transactional`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/transactional/README.md)
  - `@Transactional`,
  - `REQUIRED`, `REQUIRES_NEW`, `NESTED`,
  - isolation levels,
  - rollback rules,
  - self-invocation,
  - lazy loading i granice transakcji,
  - typowe przypadki, w których transakcja nie działa.

- [`jpa`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/jpa/README.md)
  - persistence context,
  - dirty checking,
  - lazy vs eager loading,
  - problem N+1,
  - flush vs commit,
  - transakcje w kontekście JPA,
  - typowe błędy przy pracy z encjami.

- [`mvc`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/mvc/README.md)
  - Spring MVC request lifecycle,
  - controller, service, repository,
  - `DispatcherServlet`,
  - binding requestów,
  - walidacja wejścia,
  - mapowanie odpowiedzi HTTP.

- [`exception`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/exception/README.md)
  - globalny exception handling,
  - `@ControllerAdvice`,
  - mapowanie wyjątków na statusy HTTP,
  - spójny error contract,
  - brak wycieków informacji w komunikatach błędów,
  - rozróżnienie błędów domenowych, walidacyjnych i technicznych.

- [`authorization`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/authorization/README.md)
  - różnica między authentication i authorization,
  - JWT access token i refresh token,
  - rotacja refresh tokenów,
  - OAuth2 basics,
  - resource server,
  - authorization server w minimalnym zakresie,
  - `@PreAuthorize` i SpEL,
  - autoryzacja oparta o dane: właściciel, manager, HR, organizacja, tenant.

- [`test`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/test/README.md)
  - testowanie Springa bez ładowania całego kontekstu,
  - testy warstwowe,
  - `@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`,
  - testowanie transakcji,
  - testowanie security,
  - mockowanie zależności,
  - kiedy test integracyjny ma sens,
  - property-based testing i shrinking,
  - stateful/model-based testing protokołów,
  - mutation testing przez PIT,
  - consumer-driven contracts.

### Pytania sprawdzające rozumienie

- Czy potrafisz pokazać przypadek, w którym `@Transactional` nie działa, i wyjaśnić dlaczego?
- Czy rozumiesz proxy boundaries i umiesz przewidzieć, które adnotacje Springa zostaną faktycznie zastosowane?
- Czy potrafisz wyjaśnić lifecycle beana oraz moment powstania proxy?
- Czy potrafisz rozpoznać problem z JPA: N+1, lazy loading, flush, dirty checking albo brak transakcji?
- Czy potrafisz zaprojektować spójny error contract bez ujawniania szczegółów technicznych?
- Czy rozumiesz, że autoryzacja nie kończy się na rolach?
- Czy potrafisz zaimplementować dostęp zależny od danych: właściciel, manager, HR, organizacja, tenant?
- Czy potrafisz dobrać typ testu Springa do problemu zamiast zawsze używać `@SpringBootTest`?

### Materiały

**Książki**

- *Spring in Action*
- *Spring Microservices in Action*

**Kursy**

- Spring Academy
- Spring Core
- Spring Security

---

## Blok D — [Bazy danych i performance](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/README.md)

Uzupełnieniem SQL i NoSQL są [silniki wyszukiwania](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/search_engine/README.md):
indeks odtwarzalny ze źródła prawdy, ranking, wersjonowanie dokumentów,
tombstone i paginacja wyników.


### Zakres

- #### SQL

  - [`sql/index`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/index/README.md)
      - indeksy B-tree,
        - kiedy indeks pomaga,
        - kiedy indeks szkodzi,
        - koszt zapisu vs koszt odczytu,
        - selectivity,
        - composite indexes,
        - kolejność kolumn w indeksie.
  
  - [`sql/execution_plan`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/execution_plan/README.md)
      - `EXPLAIN` i `EXPLAIN ANALYZE`,
        - sequential scan,
        - index scan,
        - bitmap index scan,
        - nested loop,
        - hash join,
        - sort,
        - koszt estymowany vs rzeczywisty,
        - odczyt planu wykonania bez zgadywania.
  
  - [`sql/transaction`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/transaction/README.md)
      - transakcje SQL,
        - `READ_COMMITTED`,
        - `REPEATABLE_READ`,
        - phantom reads,
        - atomicity i consistency,
        - spójność vs throughput,
        - dobór isolation level do problemu.
  
  - [`sql/lock`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/lock/README.md)
      - optimistic locking,
        - pessimistic locking,
        - row-level locks,
        - lock wait,
        - deadlock w bazie danych,
        - wpływ locków na przepustowość,
        - kiedy blokowanie jest poprawnym trade-offem.
  
  - [`sql/n_plus_one`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/n_plus_one/README.md)
      - problem N+1,
        - rozpoznawanie po liczbie zapytań,
        - `fetch join`,
        - entity graph,
        - batch fetching,
        - kiedy naprawa N+1 nie jest najlepszym trade-offem.
  
  - [`sql/pagination`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/pagination/README.md)
      - offset pagination,
        - keyset pagination,
        - koszt dużego offsetu,
        - stabilne sortowanie,
        - paginacja po indeksie,
        - konsekwencje dla API.
  
  - [`sql/scale`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/scale/README.md)
      - read replicas,
        - sharding,
        - partitioning,
        - connection pool,
        - cache przy bazie danych,
        - ograniczenia skalowania pionowego i poziomego.
  
  - [`sql/workload`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/workload/README.md)
      - workload OLTP vs OLAP,
        - read-heavy vs write-heavy,
        - latency vs throughput,
        - wpływ wzorca zapytań na indeksy,
        - projektowanie pod realne obciążenie.

- #### NoSQL

  - [`nosql/modeling`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/modeling/README.md)
      - modelowanie danych pod zapytania,
        - denormalizacja,
        - aggregate boundary,
        - brak joinów jako decyzja projektowa,
        - różnica między modelem relacyjnym a dokumentowym.
  
  - [`nosql/document`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/document/README.md)
      - bazy dokumentowe,
        - dokument jako agregat,
        - osadzanie vs referencje,
        - aktualizacja części dokumentu,
        - ryzyko zbyt dużych dokumentów.
  
  - [`nosql/key_value`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/key_value/README.md)
      - key-value store,
        - szybki odczyt po kluczu,
        - cache,
        - TTL,
        - ograniczenia zapytań,
        - kiedy prosty model jest zaletą.
  
  - [`nosql/wide_column`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/wide_column/README.md)
      - wide-column stores,
        - partition key,
        - clustering key,
        - projektowanie pod access pattern,
        - unikanie hot partitions,
        - ograniczenia zapytań ad hoc.
  
  - [`nosql/graph`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/graph/README.md)
      - bazy grafowe,
        - relacje jako pierwszy obywatel modelu,
        - traversale,
        - kiedy graf ma sens,
        - kiedy relacyjna baza wystarczy.
  
  - [`nosql/consistency`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/consistency/README.md)
      - eventual consistency,
        - strong consistency,
        - consistency vs availability,
        - read-your-writes,
        - conflict resolution,
      - konsekwencje spójności dla UX i logiki biznesowej.

### Pytania sprawdzające rozumienie

- Czy potrafisz dobrać indeks do zapytania i uzasadnić koszt zapisu oraz odczytu?
- Czy umiesz poprawić zapytanie lub indeks na podstawie `EXPLAIN ANALYZE`?
- Czy potrafisz dobrać isolation level i locking do konkretnego problemu biznesowego?
- Czy potrafisz rozpoznać N+1 i wybrać sensowną strategię naprawy?
- Czy rozumiesz różnicę między offset pagination a keyset pagination?
- Czy potrafisz dobrać typ bazy NoSQL do access patternu, a nie do mody?
- Czy potrafisz uzasadnić kompromis między spójnością, dostępnością i przepustowością?

### Materiały

**Książki**

- *Designing Data-Intensive Applications*
- *SQL Performance Explained*

**Kursy / strony / talks**

- Use The Index, Luke!
- PostgreSQL EXPLAIN talks
- MySQL EXPLAIN talks

---

## Blok E — [Clean Code i testowalność](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_e/README.md)

### Zakres

- Refaktoryzacja istniejącego kodu bez rewrite’u.
- Characterization tests dla czasu, losowości i statycznych zależności.
- Golden master dla trudnego kontraktu legacy.
- Branch by abstraction, shadow comparison i stopniowe przełączanie implementacji.
- Bezpieczna ewolucja kontraktu V1 → V2.
- Rozpoznawanie „czystych” zmian, które przypadkiem zmieniają semantykę.
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

### Pytania sprawdzające rozumienie

- Refaktor zwiększa czytelność i testowalność bez zmiany zachowania systemu.
- Testy łapią regresje, a nie tylko happy path.
- Czy potrafisz uzasadnić, dlaczego dany test jest unit, integration albo end-to-end?
- Czy potrafisz zabezpieczyć nieznane zachowanie przed utworzeniem seamów?
- Czy potrafisz rozłożyć większą refaktoryzację na wdrażalne kroki i odróżnić refaktoryzację od zmiany biznesowej?

### Materiały

**Książki**

- *Clean Code*
- *Working Effectively with Legacy Code*
- *Clean Architecture*

**Kursy / talks**

- Refactoring Guru
- Materiały Martina Fowlera o refaktoryzacji i testowalności

---

## Blok F — [Networking dla backend developera](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_f/README.md)

### Zakres

- DNS caching, negative TTL i rotacja adresów instancji.
- Pool acquisition, connect, TLS handshake, read timeout i deadline requestu.
- Keep-alive, ograniczona pula połączeń oraz usuwanie stale socketów.
- Koszt TLS 1.2/1.3 i ograniczenia 0-RTT.
- HTTP/1.1 kontra HTTP/2 multiplexing i limit równoległych strumieni.
- Ephemeral ports, `TIME_WAIT`, limity socketów i wpływ NAT.
- Kolejność timeoutów klienta, proxy, load balancera i downstreamu.
- Retry amplification na warstwach klient → proxy → service mesh.
- FIN, RST, half-close, silent blackhole i heartbeat.

### Pytania sprawdzające rozumienie

- Czy potrafisz wskazać fazę, w której wystąpił timeout, zamiast mówić tylko „sieć nie działa”?
- Czy rozumiesz, dlaczego zmiana DNS nie przenosi istniejącego keep-alive connection?
- Czy umiesz dobrać limit puli, timeout oczekiwania i lifetime połączenia do downstreamu oraz LB?
- Czy potrafisz policzyć zwielokrotnienie retry i wyznaczyć jednego właściciela polityki?
- Rozróżniasz EOF, reset i brak odpowiedzi wymagający timeoutu lub heartbeatów.
- Diagnozujesz wyczerpanie portów i socketów przed zmianą parametrów kernela.

### Materiały

- dokumentacja Java networking i używanego klienta HTTP,
- RFC 9110 oraz RFC 9113,
- *High Performance Browser Networking*,
- dokumentacja timeoutów używanego reverse proxy, load balancera i service mesha.

---

# Etap 2 — architektura, integracje i operacje

## Temat przewodni

Świadome decyzje architektoniczne, niezawodne integracje i umiejętność pracy bliżej produkcji.

## Cel etapu

Po tym etapie powinieneś:

- nie mylić architektury z liczbą serwisów,
- rozumieć asynchroniczność, spójność i awarie,
- umieć zaprojektować, wdrożyć i utrzymać system,
- bronić decyzji technicznej: dlaczego tak, a nie inaczej.

---

## Blok A — [Architektura: myślenie, nie frameworki](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/README.md)

Kontrakty rozwijają [projektowanie API](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/api_design/README.md),
[GraphQL](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/graphql/README.md) i [gRPC](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/grpc/README.md).
Czytaj je razem: wspólnym problemem jest granica usługi, a różnice dotyczą modelu
zapytań, kompatybilności, kosztu wywołania i propagacji deadline.


### Zakres

- [`monolith_vs_microservices`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/monolith_vs_microservices/README.md)
  - monolit,
  - modular monolith,
  - mikroserwisy,
  - koszty operacyjne, poznawcze i organizacyjne,
  - granice, przy których monolit przestaje wystarczać,
  - problemy sieci, latency i eventual consistency.

- [`ddd_strategy`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/ddd_strategy/README.md)
  - DDD strategiczne,
  - bounded context,
  - ubiquitous language,
  - context map,
  - ownership,
  - relacje między zespołami a granicami systemu.

- [`ddd_tactic`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/ddd_tactic/README.md)
  - DDD taktyczne,
  - aggregate,
  - entity,
  - value object,
  - invarianty,
  - domain service,
  - granice transakcji.

- [`clean_architecture`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/clean_architecture/README.md)
  - Hexagonal Architecture,
  - Clean Architecture,
  - domena bez Springa,
  - use case’y w warstwie application,
  - porty i adaptery,
  - framework jako szczegół implementacyjny.

- [`use_case`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/use_case/README.md)
  - modelowanie przypadków użycia,
  - application service,
  - input/output port,
  - orkiestracja logiki,
  - oddzielenie logiki aplikacyjnej od domenowej,
  - granice odpowiedzialności use case’a.

- [`integration`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/integration/README.md)
  - integracja między modułami i systemami,
  - API synchroniczne vs asynchroniczne,
  - kontrakty integracyjne,
  - zdarzenia domenowe,
  - eventual consistency,
  - odporność na błędy integracji.

### Pytania sprawdzające rozumienie

- Czy potrafisz wskazać konkretne punkty bólu, przy których modular monolith przestaje wystarczać?
- Czy umiesz uzasadnić wybór monolitu, modular monolith albo mikroserwisów bez modnych haseł?
- Czy potrafisz wyznaczyć bounded context na podstawie domeny, ownershipu i języka biznesowego?
- Czy rozumiesz różnicę między agregatem, encją, value objectem i use case’em?
- Czy potrafisz zaprojektować domenę bez zależności od Springa?
- Czy rozumiesz, co boli bardziej: wspólna baza czy sieć, latency i eventual consistency?
- Czy umiesz rozdzielić argumenty techniczne od organizacyjnych?

### Materiały

**Książki obowiązkowe**

- *Implementing Domain-Driven Design*
- *Domain-Driven Design*
- *Clean Architecture*

**Talks / artykuły**

- Martin Fowler — *Monolith First*
- InfoQ — bounded context & modular monolith

---

## Blok B — [Integracje i asynchroniczność](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/README.md)

Blok obejmuje także [WebSocket](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/websocket/README.md)
— lifecycle połączenia, wolnego klienta i reconnect — oraz
[CDC i rekoncyliację](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/cdc_reconciliation/README.md),
czyli odbudowę projekcji i naprawę danych bez powtarzania efektów biznesowych.


### Zakres

- [`kafka`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/kafka/README.md)
  - podstawy wybranego brokera,
  - topic, partition, offset,
  - consumer group,
  - ordering w ramach partycji,
  - commit offsetu,
  - delivery semantics,
  - ograniczenia exactly-once.

- [`consumer`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/consumer/README.md)
  - projektowanie konsumenta,
  - side effects,
  - transakcyjność operacji po stronie konsumenta,
  - bezpieczne ponawianie,
  - obsługa błędów w połowie operacji,
  - kiedy commitować offset.

- [`domain`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/domain/README.md)
  - event-driven flow w domenie,
  - event jako fakt biznesowy,
  - stany przejściowe, np. `PENDING`,
  - eventual consistency,
  - kompensacje,
  - obsługa opóźnionych eventów.

- [`versioning`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/versioning/README.md)
  - kontrakty eventów,
  - wersjonowanie schematów,
  - backward compatibility,
  - forward compatibility,
  - dodawanie i usuwanie pól,
  - ewolucja eventów bez psucia konsumentów.

- [`observability`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/observability/README.md)
  - logowanie przepływu eventów,
  - correlation ID,
  - metryki konsumentów,
  - lag konsumenta,
  - retry, backoff i jitter,
  - DLQ,
  - replay,
  - diagnozowanie problemów w przepływie asynchronicznym.

### Pytania sprawdzające rozumienie

- Czy potrafisz zaprojektować konsumenta odpornego na duplikaty eventów?
- Czy potrafisz bezpiecznie obsłużyć sytuację, w której konsument pada w połowie operacji?
- Czy rozumiesz, dlaczego exactly-once nie powinno być głównym celem projektu?
- Czy potrafisz zastosować retry, backoff, jitter, DLQ i replay bez psucia danych?
- Czy potrafisz zaprojektować eventy tak, żeby dało się je wersjonować?
- Czy potrafisz pracować ze stanami przejściowymi i eventual consistency?
- Czy system nie psuje danych, gdy event przyjdzie dwa razy, retry odpali się równolegle albo event dotrze z opóźnieniem?

### Materiały

**Książki**

- *Designing Data-Intensive Applications*
- *Building Event-Driven Microservices*

**Kursy / materiały**

- Confluent — Kafka Fundamentals
- RabbitMQ — Core Concepts & Reliability
- Talks o idempotent consumers, retry storms i backpressure

---

## Blok C — [DevOps: praktyczne minimum](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/README.md)


### Zakres

Stage 2C rozwija jedną aplikację referencyjną zamiast czterech podobnych projektów:

1. [`workshop`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/workshop/README.md) — kod aplikacji, lifecycle, probe’y, graceful shutdown i testy kontraktowe.
2. [`configuration`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/configuration/README.md) — konfiguracja runtime, ConfigMap, Secret, `emptyDir` kontra PVC.
3. [`docker`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/docker/README.md) — multi-stage build, cache warstw, niezmienny obraz, użytkownik non-root i obsługa `SIGTERM`.
4. [`kubernetes`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/kubernetes/README.md) — Deployment, Service, trzy probe’y, resources, securityContext, HPA i bezpieczny rolling update.
5. [`progressive_delivery`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/progressive_delivery/README.md) — canary analysis, automatyczny rollback, shadow traffic, kill switch, game day, runbook i postmortem.

Całość łączy lokalny przepływ kind/minikube z decyzją po wdrożeniu: build i load obrazu, `kubectl apply`, obserwacja rolloutu, analiza metryk oraz kontrolowana promocja lub rollback.

### Pytania sprawdzające rozumienie

- Czy potrafisz zbudować obraz aplikacji i uruchomić go lokalnie?
- Czy potrafisz wdrożyć aplikację na lokalny klaster przez kind albo minikube?
- Czy rozumiesz, co dzieje się po `kubectl apply`?
- Czy potrafisz sprawdzić problem przez `kubectl logs`, `describe`, eventy i status zasobów?
- Czy wiesz, gdzie szukać przyczyny, gdy aplikacja działa lokalnie, ale nie działa na klastrze?
- Czy umiesz odróżnić problem aplikacji, konfiguracji, sieci i infrastruktury?
- Czy potrafisz zatrzymać promocję przy małej próbce i wykonać automatyczny rollback przy regresji error rate lub p99?
- Czy umiesz uruchomić shadow bez efektów biznesowych i zaprojektować kill switch nadrzędny wobec targetingu?
- Czy weryfikujesz zgodność obu wersji aplikacji z live schema przed rolloutem i rollbackiem?
- Czy potrafisz przygotować ograniczony game day oraz przetestować runbook, timeline i kryteria recovery?

### Materiały

**Książki obowiązkowe**

- *Kubernetes Up & Running*

**Kursy / dokumentacja**

- KodeKloud
- Docker docs — multi-stage build
- Kubernetes docs

---

## Blok D — [Application Security i Secure SDLC](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_d/README.md)

### Zakres

- threat modeling, aktywa i trust boundaries,
- SSRF i kontrolowany egress,
- path traversal, request size i bezpieczny pipeline uploadu,
- allowlisted deserialization i ochrona przed mass assignment,
- zarządzanie sekretami i protokół rotacji,
- encryption in transit/at rest wraz z ownershipem kluczy,
- różnice między HTTPS, CORS, CSRF i security headers,
- logowanie bez tokenów, sekretów i PII,
- dependency/container scanning, SBOM, podpis, provenance i pinning po digest,
- negatywne testy oraz fail-closed release gate.

### Pytania sprawdzające rozumienie

- Czy potrafisz narysować przepływy danych i wskazać przekraczane granice zaufania?
- Czy nie pobierasz dowolnego URL ani pliku tylko dlatego, że przeszedł walidację składni?
- Czy rozumiesz, dlaczego CORS nie jest autoryzacją, a CSRF zależy od sposobu transportu credentiala?
- Czy potrafisz rotować sekret bez przebudowy obrazu i bez bezterminowego akceptowania starej wersji?
- Czy umiesz przypisać odpowiedzialność za TLS, encryption at rest, klucze, eksporty i backupy?
- Czy pipeline blokuje artefakt bez dowodów: SBOM, skanu, podpisu, provenance i niezmiennego digestu?
- Testujesz przede wszystkim odmowę, brak efektu ubocznego i brak wycieku w odpowiedzi lub logu.

### Materiały

- OWASP ASVS i OWASP Cheat Sheet Series,
- OWASP SAMM,
- NIST Secure Software Development Framework,
- dokumentacja używanego secret managera, KMS i narzędzi supply-chain.

---

# Etap 3 — skalowanie, niezawodność i cloud

## Temat przewodni

Projektowanie systemów, które działają pod obciążeniem, są obserwowalne i dają się sensownie utrzymać w chmurze.

## Cel etapu

Po tym etapie powinieneś:

- projektować system na tablicy i bronić decyzji,
- używać metryk, logów i trace’ów zamiast zgadywania,
- rozumieć skalę, awarie i koszty w chmurze,
- myśleć w kategoriach SLO, degradacji i trade-offów.

---

## Blok A — [System Design: skalowanie i odporność](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/README.md)

### Zakres

- [`concepts`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/concepts/README.md)
  - skalowanie pionowe vs poziome,
  - stateless API,
  - bottlenecki przy wzroście RPS,
  - throughput vs latency,
  - capacity planning,
  - podstawowe założenia do projektowania systemu.

- [`metrics`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/metrics/README.md)
  - RPS,
  - latency p50, p95, p99,
  - error rate,
  - saturation,
  - CPU, memory, network, disk IO,
  - queue depth,
  - metryki cache, bazy danych i zewnętrznych integracji.

- [`implementation`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/implementation/README.md)
  - autoscaling,
  - caching z Redisem,
  - TTL, eviction, LRU vs LFU,
  - cache stampede,
  - single-flight,
  - jitter,
  - rate limiting: per IP, per API key, token bucket, sliding window,
  - circuit breaker,
  - retry, backoff i graceful degradation,
  - propagacja deadline i podział budżetu czasu,
  - przejście od JMH do testów całej usługi: load, stress, spike i soak,
  - open/closed workload, coordinated omission i hipoteza capacity,
  - p95/p99, saturation, kontrolowana degradacja i ocena autoskalowania,
  - twarde kryteria przerwania eksperymentu,
  - izolacja tenantów w danych, cache, eventach i kontekście requestu,
  - noisy-neighbor protection przez per-tenant quota i bulkhead,
  - klasyfikacja PII, retention, anonimizacja oraz audyt dostępu,
  - propagacja usunięcia do cache, downstreamów i procesu odtwarzania backupu,
  - współdzielony retry budget i retry storm,
  - osobne bulkheady dla zależności,
  - bounded queue, backpressure i load shedding,
  - timeout klienta kontra anulowanie downstreamu.

### Pytania sprawdzające rozumienie

- Czy potrafisz narysować system i wskazać jego główne bottlenecki?
- Czy potrafisz oszacować, który element padnie przy określonym RPS i dlaczego?
- Czy umiesz dobrać cache, rate limiting, autoscaling albo circuit breaker do konkretnego problemu?
- Czy rozumiesz różnicę między poprawą throughputu a obniżeniem latency?
- Czy potrafisz uzasadnić decyzje na podstawie założeń, metryk i ograniczeń systemu?

### Materiały

**Książki**

- *Designing Data-Intensive Applications*
- *System Design Interview — An Insider’s Guide*

**Talks / artykuły**

- High Scalability
- Martin Fowler — caching and resilience patterns

---

## Blok B — [Observability: dowody zamiast intuicji](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_b/README.md)

### Zakres

- [`structured_logs`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_b/structured_logs/README.md)
  - logi strukturalne w JSON,
  - correlation ID,
  - request ID,
  - request → downstream correlation,
  - poziomy logowania,
  - logowanie błędów bez utraty kontekstu.

- [`prometheus`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_b/prometheus/README.md)
  - metryki aplikacyjne i systemowe,
  - throughput,
  - latency,
  - error rate,
  - p95 i p99,
  - saturation,
  - podstawy Prometheus i PromQL.

- [`tracing`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_b/tracing/README.md)
  - distributed tracing,
  - OpenTelemetry,
  - trace ID i span ID,
  - API → cache → DB,
  - sampling,
  - trace context propagation,
  - szukanie źródła latency w zależnościach downstream.

- [`alerts`](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_b/alerts/README.md)
  - alerty oparte o symptomy, nie same przyczyny,
  - alerty dla latency, error rate i saturation,
  - progi alertów,
  - unikanie alert fatigue,
  - runbooki dla typowych awarii,
  - reakcja na DB down, Redis down, broker lag i error rate spike.

### Pytania sprawdzające rozumienie

- Czy potrafisz wskazać metrykę, która potwierdza problem z systemem?
- Czy potrafisz przejść od metryki do logów, trace’ów i technicznej hipotezy?
- Czy umiesz użyć correlation ID do prześledzenia requestu przez zależności downstream?
- Czy potrafisz rozróżnić problem latency, error rate, saturation i dependency failure?
- Czy potrafisz zaprojektować alert, który opisuje realny objaw problemu?
- Czy masz runbook dla typowych awarii: DB down, Redis down, broker lag i spike błędów?

### Materiały

**Książki**

- *Observability Engineering*
- *Site Reliability Engineering*

**Dokumentacja / kursy**

- OpenTelemetry docs
- Prometheus best practices
- Grafana dashboards & alerts

---

## Blok C — [Cloud: jeden provider, ale porządnie](backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_c/README.md)

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
  - restore i regularny restore drill,
  - mierzalne RPO i RTO,
  - HA kontra regionalne disaster recovery.
- Networking:
  - VPC,
  - public/private subnets,
  - security groups,
  - routing.
- Operacje i bezpieczeństwo:
  - Infrastructure as Code i wykrywanie driftu,
  - workload identity i minimalne IAM,
  - regionalny failover i fencing starego primary,
  - rollback obrazu oraz kompatybilne migracje expand/contract,
  - degradacja przy utracie bazy, cache i brokera.
- Koszt vs wydajność:
  - główne cost drivers,
  - autoscaling vs overprovisioning,
  - cache jako narzędzie wydajności i oszczędności.

### Pytania sprawdzające rozumienie

- Czy potrafisz wdrożyć prostą aplikację u wybranego cloud providera?
- Czy rozumiesz różnicę między compute, storage i networkingiem w praktycznym deployu?
- Czy potrafisz skonfigurować stateless workload z autoscalingiem?
- Czy potrafisz wyjaśnić podstawy VPC, subnetów, routingu i security groups?
- Czy potrafisz uruchomić managed database z backupem, restore i opcjonalną read replicą?
- Czy potrafisz zmierzyć RPO/RTO i udowodnić restore testem integralności oraz critical journey?
- Czy potrafisz odtworzyć desired state z IaC, wykryć drift i ograniczyć IAM per workload?
- Czy potrafisz przeprowadzić failover bez split-brain oraz ocenić, czy rollback aplikacji jest zgodny ze schematem?
- Czy potrafisz wskazać główne cost drivers danego rozwiązania?
- Czy potrafisz odróżnić overprovisioning od sensownego autoscalingu?
- Czy potrafisz powiedzieć: „To rozwiązanie jest technicznie poprawne, ale zbyt drogie. Zróbmy inaczej.”?

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

## Zasada 1: łącz opis z wykonywalnym dowodem

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

Opis teorii zestawiaj z kodem i obserwacją: jaki niezmiennik sprawdzono, w jakim środowisku i czego wynik nie dowodzi.

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

# Jak oceniać rozumienie materiału

Poniższe artefakty pomagają sprawdzić rozumienie i odkryć luki. Nie są warunkiem „ukończenia” kompendium:

- projekty pokazujące lokalną poprawność, granice integracji i awarie rozproszone,
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

---

## Utrzymanie dokumentacji

Z katalogu głównego repozytorium uruchom `pwsh -File scripts/check-documentation.ps1`.
Kontrola sprawdza lokalne odsyłacze, karty laboratoriów, wskazane moduły i testy
oraz zgodność profili infrastrukturalnych z CI. Nie zastępuje uruchomienia
wskazanych testów ani merytorycznego przeglądu komentarzy.
