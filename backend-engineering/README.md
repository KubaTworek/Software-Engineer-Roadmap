# Backend engineering — kompendium

Ten moduł jest praktycznym kompendium zagadnień istotnych dla Java Backend
Engineera. Podział na etapy porządkuje materiał od fundamentów do problemów
systemów produkcyjnych, ale **nie jest checklistą ani systemem zaliczania**.
Do tematów warto wracać wraz ze zmianą skali, domeny i ograniczeń projektu.

Kod ma kilka form: małe eksperymenty, przykłady celowo błędne, testy zachowania
oraz samodzielne aplikacje demonstracyjne. Przed oceną fragmentu kodu przeczytaj
najbliższy `README.md` — uproszczenie może być częścią ćwiczenia.

## Trzy osie nawigacji

Materiał można czytać na dwa uzupełniające się sposoby:

- **etapami** — gdy chcesz budować kontekst od mechanizmu języka i frameworka
  do architektury oraz operacji;
- **przekrojowo** — gdy rozwiązujesz konkretny problem, na przykład duplikaty,
  utratę aktualizacji, przeciążenie albo trudny do zdiagnozowania wzrost latency.
- **przez technologię** — gdy chcesz prześledzić Reactive Streams, GraphQL,
  gRPC, WebSocket albo dedykowany silnik wyszukiwania od fundamentu do produkcji.

Numer etapu opisuje poziom kontekstu, a nie rangę tematu ani procent ukończenia.
To samo pojęcie celowo wraca w kilku miejscach, ponieważ inne gwarancje daje
pojedynczy proces, inne baza danych, a jeszcze inne system rozproszony.

## Roadmapa etapowa — od mechanizmu do systemu

| Obszar | Blok | Najważniejsze zagadnienia |
| --- | --- | --- |
| Fundamenty JVM i backendu | [Stage 1 / Block A](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/README.md) | współbieżność, czas i schedulery, synchronizacja, executory, virtual threads oraz Reactive Streams |
| Wydajność Javy | [Stage 1 / Block B](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/README.md) | JVM, GC, JIT, profilowanie, alokacje i benchmarki |
| Spring pod maską | [Stage 1 / Block C](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/README.md) | lifecycle, proxy, transakcje, JPA, MVC, security, test slices, property/stateful i mutation testing |
| Dane i persystencja | [Stage 1 / Block D](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/README.md) | SQL, indeksy, plany wykonania, transakcje, NoSQL oraz silniki wyszukiwania |
| Jakość projektu | [Stage 1 / Block E](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_e/README.md) | characterization tests, golden master, branch by abstraction, ewolucja kontraktu i refaktoryzacja semantyczna |
| Networking aplikacyjny | [Stage 1 / Block F](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_f/README.md) | DNS, TCP/TLS, timeouty, keep-alive, pooling, HTTP/2, sockety i retry amplification |
| Modelowanie i architektura | [Stage 2 / Block A](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/README.md) | DDD, Clean Architecture, REST, GraphQL, gRPC, modularny monolit, mikroserwisy i sagi |
| Systemy zdarzeniowe | [Stage 2 / Block B](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/README.md) | Kafka, WebSocket, idempotencja, retry, DLQ, CDC, rekoncyliacja, replay i online backfill |
| Delivery i operacje | [Stage 2 / Block C](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/README.md) | konfiguracja, Docker, Kubernetes, canary, rollback, game day i incident response |
| Application Security | [Stage 2 / Block D](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_d/README.md) | threat modeling, SSRF, uploady, sekrety, szyfrowanie, bezpieczne logi i supply chain |
| System design | [Stage 3 / Block A](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/README.md) | estymacja, skalowanie, overload control, koordynacja oraz architektura wielodostępnego SaaS |
| Observability | [Stage 3 / Block B](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_b/README.md) | logi strukturalne, metryki, tracing, alerty i SLO |
| Cloud architecture | [Stage 3 / Block C](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_c/README.md) | stateless compute, managed data, RPO/RTO, DR, IaC, IAM, odporność i koszty |

Roadmapa ma trzy poziomy rozumowania:

1. **Stage 1 — mechanizm i lokalna poprawność.** Co robi JVM, Spring lub baza
   oraz jakie niezmienniki potrafi zagwarantować pojedyncza aplikacja.
2. **Stage 2 — granice i współpraca komponentów.** Gdzie kończy się transakcja,
   jak ewoluują kontrakty i co dzieje się przy ponownym dostarczeniu komunikatu.
3. **Stage 3 — zachowanie pod skalą i awarią.** Jak rozwiązanie wpływa na cały
   system, jak je obserwować, eksploatować, odtwarzać i utrzymywać kosztowo.

Reactive Streams, silniki wyszukiwania, GraphQL, gRPC i WebSocket są
wykonywalnymi laboratoriami swoich bloków. Nie tworzą czwartego etapu ani osobnej
ścieżki poza kompendium.

## Globalny indeks pojęć

Każdy wiersz jest osobną ścieżką przez kompendium. Kolumna „Fundament” wyjaśnia
mechanizm, „Implementacja” prowadzi do wykonywalnego zastosowania, a
„Zachowanie produkcyjne” pokazuje awarie, skalę, monitoring i ograniczenia.

| Pojęcie | Fundament | Implementacja | Zachowanie produkcyjne |
| --- | --- | --- | --- |
| Współbieżność i własność stanu | [JMM, atomowość i synchronizacja](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/README.md) | [virtual threads i ograniczony downstream](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/virtual_threads/README.md) | [capacity, kolejki i backpressure](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/README.md) |
| Reactive Streams | [kontrakt demand i anulowania](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/reactive_streams/README.md) | [Publisher respektujący backpressure](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/reactive_streams/README.md) | [bounded concurrency i ochrona downstreamu](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/implementation/overload/README.md) |
| Czas i zadania okresowe | [Instant, strefy, DST i zegar monotoniczny](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/temporal_correctness/README.md) | [deadline, checkpoint, misfire i deduplikacja](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/temporal_correctness/README.md) | [lease, clock skew i fencing](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/concepts/coordination/README.md) |
| Wydajność i latency | [JMH, JFR, GC i alokacje](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/README.md) | [capacity, load/stress/spike/soak i coordinated omission](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/implementation/tests/README.md) | [p95/p99, saturation, degradacja i autoskalowanie](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/implementation/tests/README.md) |
| Transakcje i spójność | [granice transakcji Spring](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/transactional/README.md) | [izolacja i anomalie PostgreSQL](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/transaction/README.md) | [offset, baza i semantyka dostarczenia](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/kafka/README.md) |
| Locking i koordynacja | [locki JVM i deadlock](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/deadlock/README.md) | [blokady PostgreSQL](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/lock/README.md) | [lease, fencing i leader election](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/concepts/coordination/README.md) |
| Poprawność systemu rozproszonego | [historia zamiast końcowego wyniku](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/concepts/correctness/README.md) | [deterministyczny scheduler, awarie i retry](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/concepts/correctness/README.md) | [safety, liveness i liniowalność](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/concepts/correctness/README.md) |
| Idempotencja | [kontrakt żądania HTTP](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/README.md) | [idempotentny consumer i trwały marker](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/consumer/README.md) | [Redis, Outbox, worker i efekt downstream](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_c/README.md) |
| Deadline, timeout i retry | [timeout i anulowanie pracy](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/cancel/README.md) | [deadline propagation, retry budget i circuit breaker](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/implementation/overload/README.md) | [retry storm, bulkhead, load shedding i anulowanie downstreamu](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/implementation/overload/README.md) |
| Networking | [DNS, TCP/TLS i semantyka timeoutów](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_f/README.md) | [keep-alive, pooling, HTTP/2 i half-open](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_f/README.md) | [retry amplification, LB/mesh i diagnostyka](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_f/README.md) |
| Messaging i Outbox | [zdarzenia domenowe i granica integracji](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/integration/README.md) | [Kafka, consumer, retry i DLQ](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/README.md) | [at-least-once, relay i rekoncyliacja](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_c/README.md) |
| CDC i naprawa projekcji | [Outbox jako stabilny fakt integracyjny](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/use_case/README.md) | [snapshot→stream, wersja źródłowa, poison i reconciliation](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/cdc_reconciliation/README.md) | [replay, online backfill, zweryfikowany cutover i monitoring WAL](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/cdc_reconciliation/README.md) |
| Cache | [współbieczny dostęp do mapy](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/concurrent_hash_map/README.md) | [cache-aside, TTL i stampede](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/concepts/README.md) | [degradacja Redis i ochrona źródła prawdy](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_c/README.md) |
| Rate limiting i przeciążenie | [atomowa decyzja oraz TTL](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/key_value/README.md) | [bounded queue, concurrency limit i load shedding](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/implementation/overload/README.md) | [limit globalny, autoscaling i backpressure](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_c/README.md) |
| Wielodostępność i lifecycle danych | [autoryzacja tenant-aware](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/authorization/README.md) | [izolacja, quota, cache keys, PII i audyt](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/implementation/saas/README.md) | [propagacja usunięcia, backup restore i data governance](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/implementation/saas/README.md) |
| Model danych i skalowanie | [SQL kontra NoSQL](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/README.md) | [access pattern, partycja i bucketing](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/README.md) | [sharding, consistent hashing i hot partition](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/README.md) |
| Wyszukiwanie | [inverted index i ranking](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/search_engine/README.md) | [wersja dokumentu, tombstone i `search_after`](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/search_engine/README.md) | [CDC, reindex, drift detection i odbudowa](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/cdc_reconciliation/README.md) |
| Granice architektury | [use case i separacja zależności](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_e/README.md) | [DDD, Clean Architecture i ArchUnit](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/README.md) | [monolit modularny kontra mikroserwisy](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/monolith_vs_microservices/README.md) |
| Kontrakt HTTP API | [Spring MVC i granica protokołu](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/mvc/README.md) | [metody HTTP, ETag, idempotencja, Problem Details i OpenAPI](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/api_design/README.md) | [kompatybilność, async operations i webhook redelivery](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/api_design/README.md) |
| GraphQL | [graf pól i koszt zapytania](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/graphql/README.md) | [batching oraz field-level authorization](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/graphql/README.md) | [limity, persisted queries i tracing resolverów](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/graphql/README.md) |
| gRPC | [HTTP/2, deadline i status](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/grpc/README.md) | [ewolucja Protobuf i retry policy](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/grpc/README.md) | [mTLS, flow control, health checking i load balancing](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/grpc/README.md) |
| WebSocket | [lifecycle długiego połączenia](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/websocket/README.md) | [reconnect, replay i bounded buffer](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/websocket/README.md) | [backplane, heartbeat i stateless routing](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/websocket/README.md) |
| Ewolucja kontraktu | [bezpieczna zmiana V1 → V2](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_e/README.md) | [wersjonowanie eventów i kompatybilność](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/versioning/README.md) | [rollout oraz rollback aplikacji i migracji](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_c/README.md) |
| Observability | [log, metryka i span](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/observability/README.md) | [OpenTelemetry i propagacja `traceparent`](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_b/pipeline/README.md) | [Collector, alert, SLO i runbook](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_b/README.md) |
| Application Security | [JWT, OAuth2 i ownership zasobu](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/authorization/README.md) | [threat boundaries, SSRF, upload, CSRF i bezpieczne logi](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_d/README.md) | [rotacja sekretów, encryption ownership, SBOM i release gate](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_d/README.md) |
| Konfiguracja i bezpieczeństwo | [properties, precedence i walidacja](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/configuration/README.md) | [runtime config, kontener i Kubernetes](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/README.md) | [workload identity, minimalne IAM i sekrety](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_c/README.md) |
| Testowanie i zmiana legacy | [test doubles, property/stateful i mutation testing](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/test/README.md) | [contract testing, golden master i branch by abstraction](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_e/README.md) | [consumer-driven contracts i Testcontainers](#testy-z-prawdziwą-infrastrukturą) |
| Progressive delivery i incydenty | [expand/contract i kompatybilny rollback](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/migration/README.md) | [canary, shadow, kill switch i automatyczny rollback](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/progressive_delivery/README.md) | [game day, timeline, runbook i postmortem](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/progressive_delivery/README.md) |
| Delivery i ciągłość działania | [migracje expand/contract](src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/migration/README.md) | [obraz, probes, rollout i graceful shutdown](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/workshop/README.md) | [RPO, RTO, restore drill, DR i IaC drift](src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_c/README.md) |

## Polecane ścieżki problemowe

Nie trzeba czytać całego etapu, aby prześledzić jeden problem:

- **„Jak nie sprzedać tego samego zasobu dwa razy?”** — atomowość JVM →
  transakcja i locking PostgreSQL → idempotencja → lease i fencing.
- **„Jak niezawodnie wykonać efekt po zmianie danych?”** — granica transakcji →
  Outbox → at-least-once consumer → trwały marker → rekoncyliacja.
- **„Jak odbudować albo naprawić read model?”** — spójny snapshot → watermark →
  live tail → wersjonowana projekcja → drift detection → replay lub online backfill
  → zweryfikowany cutover.
- **„Dlaczego system zwalnia pod obciążeniem?”** — JFR/JMH → pule i SQL →
  backpressure → histogram latency → trace zależności.
- **„Dlaczego wywołanie innej usługi timeoutuje?”** — DNS → pula → TCP/TLS →
  deadline requestu → timeout proxy/LB → retry amplification → trace zależności.
- **„Jak zaprojektować i bezpiecznie zmienić API?”** — semantyka HTTP →
  idempotency key i ETag → OpenAPI → kompatybilne V1/V2 → wersjonowanie eventów
  → rollout, obserwacja i rollback.
- **„Jak bezpiecznie wdrożyć zmianę?”** — expand → canary → analiza error rate/p99 →
  promote albo rollback → kill switch → timeline, runbook i postmortem.
- **„Dlaczego zadanie wykonało się dwa razy albo o złej godzinie?”** — Instant
  kontra czas lokalny → DST → trwały checkpoint → misfire → idempotencja → lease
  i fencing.
- **„Czy sukces wszystkich requestów dowodzi poprawności?”** — historia operacji →
  timeout po commit → retry → niezmiennik safety → postęp liveness → checker
  liniowalności i reprodukowalny seed.
- **„Co się stanie po utracie zależności lub regionu?”** — timeout i retry →
  kontrolowana degradacja → SLO i runbook → RPO/RTO oraz restore drill.

Przy każdym temacie warto odpowiedzieć na pięć pytań: **jaki niezmiennik
chronimy, jaką gwarancję daje mechanizm, gdzie ta gwarancja się kończy, jaki
failure mode pozostaje oraz jak zobaczymy go w produkcji**. Ta piątka łączy kod,
testy i operacje lepiej niż samo zapamiętanie nazwy wzorca.

## Jak korzystać z kompendium

- Zacznij od krótkiej **karty materiału** bezpośrednio pod tytułem README. Pokazuje
  zakres, typowy błąd, test będący najkrótszym dowodem, role klas i granicę modelu.
- Zacznij od problemu, który chcesz zrozumieć, nie od numeru etapu.
- Uruchom przykład i jego testy, a następnie zmień jedno założenie.
- Porównuj rozwiązania przez ich gwarancje i koszty, nie przez liczbę użytych wzorców.
- Dla wydajności zapisuj hipotezę i pomiar; dla niezawodności zapisuj failure mode.
- Traktuj przykłady `broken`, `naive`, `single-thread` i podobne jako kontrprzykłady,
  jeśli najbliższa dokumentacja nie mówi inaczej.

Pełny kontrakt redakcyjny znajduje się w
[`EDITORIAL_STANDARD.md`](EDITORIAL_STANDARD.md). Obejmuje układ „problem →
niezmiennik → naiwny przykład → poprawne rozwiązanie → test → ograniczenia
produkcyjne”, znaczenie zakresów oraz role klas edukacyjnych.

## Powtórzenia są kolejnymi granicami problemu

Ten sam termin może pojawić się w kilku etapach, ale nie powinien oznaczać kopii
tego samego przykładu. Każde powtórzenie przesuwa granicę gwarancji:

| Problem | Kolejne poziomy |
| --- | --- |
| współdzielony zapis | lock JVM → transakcja/blokada PostgreSQL → lease → fencing → test historii |
| ponowienie operacji | retry metody → idempotency key HTTP → marker consumera → rekoncyliacja efektu |
| czas | `Clock` w domenie → deadline requestu → misfire schedulera → clock skew koordynatora |
| wydajność | poprawność workloadu → JMH → JFR/GC → load/stress/spike/soak → capacity i saturation |
| zdarzenia | domain event → integration event → Outbox/Kafka → CDC/replay → naprawa projekcji |
| obserwowalność | log/metryka/span → korelacja → Collector → alert/SLO → runbook i incydent |

README niższego poziomu powinno linkować do następnego poziomu zamiast ponownie
tłumaczyć całość. Powtórzenie bez nowego niezmiennika, failure mode albo granicy
infrastrukturalnej jest kandydatem do usunięcia.

## Konwencje pakietów

- Deklaracja `package` odpowiada ścieżce od katalogu źródłowego `src/main/java`
  albo `src/test/java`; nazwy tych katalogów nie są segmentami pakietu.
- Pakiety nazywamy według odpowiedzialności, np. `domain`, `application`,
  `infrastructure`, `persistence` i `resilience`, używając poprawnych nazw
  angielskich i wyłącznie małych liter.
- Samodzielne laboratorium Maven ma własny katalog `src` oraz własną bazową
  przestrzeń nazw. Nie dziedziczy pakietu wynikającego z miejsca, w którym jego
  katalog projektu znajduje się w repozytorium.
- Test powinien zachowywać pakiet testowanej klasy albo używać jawnego pakietu
  testowego; katalog `src/test/java` również nie trafia do deklaracji `package`.

## Buildy Maven

Wrapper znajduje się w tym katalogu i jest wspólnym, przypiętym sposobem
uruchamiania wszystkich laboratoriów. Z katalogu `backend-engineering` użyj
`./mvnw` na Linux/macOS albo `.\mvnw.cmd` w PowerShell.

| Laboratorium | Java | Spring Boot | Komenda z katalogu `backend-engineering` |
| --- | ---: | ---: | --- |
| Główne kompendium i testy Stage 1–3 | 21 | 4.0.3 | `.\mvnw.cmd verify` |
| Wykonywalny JAR benchmarków JMH | 21 | — | `.\mvnw.cmd -Pjmh-runner -DskipTests package` |
| Stage 2C — jedna aplikacja i warstwy dostarczania | 21 | 4.0.3 | `.\mvnw.cmd -f src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/workshop/pom.xml verify` |
| Stage 3C — cloud architecture | 21 | 4.0.3 | `.\mvnw.cmd -f src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_c/pom.xml verify` |

Zwykłe `verify` uruchamia szybkie testy bez kontenerów. Kontrakty zależne od
PostgreSQL, Redis, Kafki i OpenSearch należą do profilu `infrastructure-tests`.
Rozdzielenie jest jawne: szybki build nie udaje potwierdzenia semantyki
konkretnego silnika.

## Testy z prawdziwą infrastrukturą

Pięć suite'ów oznaczonych tagiem JUnit `infrastructure` uzupełnia szybkie testy
o kontrakty zależne od implementacji konkretnego silnika:

| Test | Co potwierdza |
| --- | --- |
| `PostgreSqlConcurrencyContainerTest` | blokadę `FOR UPDATE`, kod błędu PostgreSQL przy `lock_timeout`, `SKIP LOCKED` oraz wspólny commit/rollback danych biznesowych i outboxa |
| `RedisAtomicCounterContainerTest` | atomowe wykonanie `INCR` + `PEXPIRE` przez Lua, zachowanie wszystkich równoległych inkrementacji i obecność TTL |
| `PostgreSqlExecutableLabTest` | migracje Flyway, plany zapytań, paginację, poziomy izolacji, optimistic locking i constrainty |
| `KafkaPostgresSemanticsTest` | kolejność w partycji, redelivery, granicę offset–SQL, idempotencję, retry/DLQ i awarię relaya outbox |
| `OpenSearchContainerTest` | mapping, analizę tekstu, external versioning i stabilne sortowanie pod `search_after` |

Pełny zestaw infrastrukturalny uruchamia wyłącznie te suite'y:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify
```

Profil wymaga działającego Docker Engine. Brak Dockera albo problem z pobraniem
obrazu kończy build błędem — testy nie używają `disabledWithoutDocker`, ponieważ
zielony wynik tego profilu ma oznaczać rzeczywiste wykonanie kontraktów.

Workflow `backend-engineering-ci.yml` ma osobny job dockerowy dla tego profilu.
Podstawowa macierz nadal wykonuje szybkie `verify`, więc oba wyniki mają różne,
czytelne znaczenie.

## Security build i SBOM

Stage 2D dodaje osobny, wolniejszy profil supply-chain. Generuje CycloneDX SBOM
i uruchamia OWASP Dependency-Check dla zależności runtime:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Psecurity-scan verify
```

SBOM powstaje jako `target/backend-engineering-sbom.json`, a raporty podatności
jako `target/dependency-check-report.*`. Skan jest fail-closed: błąd narzędzia
również przerywa build. Pierwsze pobranie danych NVD może być długie, dlatego
pipeline powinien korzystać z bezpiecznie przekazanego klucza NVD i cache bazy.
Workflow uruchamia ten profil co tydzień oraz na żądanie przez
`workflow_dispatch`; sekret `NVD_API_KEY` przyspiesza pobieranie danych, ale nie
jest wymagany do ręcznego uruchomienia profilu.
Szczegóły modelu zagrożeń oraz polityki release opisuje
[Stage 2D](src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_d/README.md).
