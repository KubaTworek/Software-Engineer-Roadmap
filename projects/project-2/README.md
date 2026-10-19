# Marketplace Order Fulfillment Platform

Projekt portfolio integrujący **cały Stage 2** modułu `backend-engineering`: od
granic domenowych i ewolucji API, przez niezawodny messaging, po wdrożenie,
incydenty i Secure SDLC. To modularny monolit — jeden artefakt wdrożeniowy, ale
osobne bounded contexts i schematy danych.

Stack bazowy jest spójny z kompendium: **Java 21, Spring Boot 4.0.3, Jackson 3,
PostgreSQL 16, Kafka oraz Gradle 9.0**.

## Zacznij tutaj

| Chcę zobaczyć | Najważniejszy kod | Test dowodzący |
|---|---|---|
| agregat i invariants | `ordering/domain/Order` | `OrderTest` |
| dependency rule | pakiety `domain/application/api/infrastructure` | `ArchitectureRulesTest` |
| atomową granicę use case + outbox | `PlaceOrderUseCase` | `MarketplaceKafkaPostgresIntegrationTest` |
| at-least-once i idempotencję | `KafkaConsumerWorker`, `ConsumerUnitOfWork` | `KafkaConsumerFlowTest` |
| optimistic locking | `JdbcOrderRepository` | `OrderOptimisticLockingTest` |
| wieloreplikowy outbox | `claimNew()` + `FOR UPDATE SKIP LOCKED` | `OutboxFlowTest` i integration test |
| trwałą sagę, timeout i kompensację | `OrderFulfillmentProcessManager`, `Release*Handler` | `CompensationFlowTest` i runtime integration |
| kompatybilność eventów | `OrderPlacedV1/V2` | `EventVersioningCompatibilityTest` |
| CDC i naprawę read modelu | `CdcProjectionPipeline`, `ProjectionReconciler` | `CdcProjectionPipelineTest` |
| prawdziwy strumień CDC | `DebeziumOrderChangeAdapter` | `DebeziumCdcIntegrationTest` |
| idempotencję między replikami | `JdbcIdempotencyKeyRegistry` | `DurableBoundariesIntegrationTest` |
| niepewny wynik płatności | `ReconcileUnknownPaymentUseCase` | `ReconcileUnknownPaymentUseCaseTest` |
| REST/GraphQL/gRPC | `ordering/api`, `api/graphql`, `api/grpc` | `ProtocolSemanticsTest`, `OrderingGrpcTransportTest` |
| lifecycle WebSocket | `OrderWebSocketHub`, `ConnectionSession` | `OrderWebSocketHubTest` |
| progressive delivery | `CanaryDecisionPolicy` | `CanaryDecisionPolicyTest` |
| granice bezpieczeństwa | `SecurityConfiguration`, `OrderOwnership` | `MarketplaceSecurityIntegrationTest` |

## Mapa Stage 2

### Stage 2A — architektura i API

- DDD: agregaty, value objects, invariants, domain events i bounded contexts;
- Clean Architecture: kierunek zależności wymuszony przez ArchUnit;
- modularny monolit z osobnym schematem PostgreSQL dla każdego modułu;
- jawna granica lokalnej transakcji i trwały process manager dla procesu wielomodułowego;
- REST: walidacja, Problem Details, `Idempotency-Key`, `ETag` i `If-Match`;
- wersjonowane kontrakty oraz wykonywalne adaptery REST, GraphQL i gRPC/Protobuf;
- GraphQL query-cost/N+1/auth oraz propagacja deadline w gRPC.

### Stage 2B — komunikacja i spójność

- transactional outbox oraz atomowy claim rekordu przez `SKIP LOCKED`;
- Kafka: rzeczywiste `@KafkaListener`, osobne grupy bounded contextów, klucz
  partycjonowania i manual acknowledgement po efekcie;
- atomowy efekt konsumenta + marker idempotencji w PostgreSQL;
- klasyfikacja błędów, retry budget, exponential backoff, jitter, DLQ i replay;
- schema evolution `OrderPlacedV1 -> V2` z tolerancją nowych pól;
- saga, komendy kompensacyjne, idempotentne zwolnienie zasobów i eventual consistency;
- CDC, snapshot/replay, rekoncyliacja i naprawa projekcji bez efektów biznesowych;
- WebSocket: rzeczywisty handshake/transport, wygasanie autoryzacji i bounded queue dla wolnego klienta.

### Stage 2C — runtime i operacje

- zewnętrzna konfiguracja przez środowisko;
- wieloetapowy, nie-root Docker image z poprawnym przekazaniem `SIGTERM`;
- probes, requests/limits, `securityContext`, PDB, HPA i graceful termination;
- canary oparty o minimalną próbkę, error rate, p99 i regresję do baseline;
- rollback, kill switch, game day, incident timeline i weryfikacja recovery.

### Stage 2D — Application Security i Secure SDLC

- threat model oraz trust boundaries;
- ochrona webhooków: HMAC, timestamp, delivery-id i rotacja klucza;
- SSRF: allowlista, HTTPS i walidacja rozwiązanego adresu;
- upload: limit strumienia, typ treści i ochrona przed path traversal;
- sekrety poza Git, least privilege i fail-closed;
- SBOM/dependency scanning, kontrola obrazu i negatywne testy granic.

## Architektura

```text
HTTP / GraphQL / gRPC
          |
       api adapter
          v
 application use case ----> port <---- infrastructure adapter
          |
       aggregate
          |
  aggregate + outbox (jedna transakcja PostgreSQL)
          |
     outbox claim -> Kafka -> consumer effect + processed marker
                                      |
                            commit offset po transakcji
```

Bounded contexts:

- `catalog` — produkt i cena źródłowa;
- `ordering` — cykl życia zamówienia;
- `payment` — rezerwacja/zwolnienie środków;
- `inventory` — dostępność i rezerwacja stocku;
- `fulfillment` — rozpoczęcie realizacji;
- `notification` — reakcja na fakty biznesowe;
- `integration` — published language, outbox, Kafka, DLQ i CDC;
- `process` — stan procesu wykraczającego poza jeden agregat.

`shared` zawiera wyłącznie mały kernel i techniczne mechanizmy przekrojowe. Nie jest
workiem na modele należące do konkretnych kontekstów.

## Najważniejsze gwarancje i ich granice

### Lokalna transakcja, nie distributed transaction

Zapis Order i jego outboxa jest atomowy. Konsument w osobnej transakcji zapisuje
efekt oraz `processed_events`. Kafka offset jest zatwierdzany dopiero potem. Awaria
po lokalnym commicie, lecz przed commitem offsetu powoduje redelivery — marker
unikalny po `(event_id, consumer_name)` zatrzymuje ponowny efekt.

To jest **at-least-once**, nie exactly-once end-to-end. Zewnętrzna bramka płatnicza
również potrzebuje idempotency key.

### Kolejność

`aggregateId/orderId` jest kluczem partycjonowania, więc Kafka zachowuje kolejność
tylko w obrębie partycji. Commit jest przechowywany per `(topic, group, partition)`.
Globalna kolejność nie istnieje i model biznesowy nie może jej zakładać.
Process manager przyjmuje dlatego fakt Payment/Inventory także przed własną kopią
`OrderPlaced`; kolejność przyczynowa między różnymi topicami nie jest gwarantowana.

### Lost update

Payment i Inventory mogą równocześnie modyfikować Order. Kolumna `version` i
`UPDATE ... WHERE version = ?` wykrywają konflikt. Przegrywający handler może
odczytać świeży stan i powtórzyć operację w ramach ograniczonego retry budgetu.

### Outbox w wielu replikach

Samo `SELECT status=NEW` pozwala kilku workerom wysłać ten sam rekord równocześnie.
`claimNew()` wykonuje `FOR UPDATE SKIP LOCKED` i zmienia stan na `PROCESSING` w jednym
statementcie. Porzucony claim jest odzyskiwany. Awaria po publish, przed oznaczeniem
`PUBLISHED`, nadal może dać duplikat — dlatego potrzebna jest idempotencja odbiorcy.

### Saga

Rezerwacja płatności i stocku nie jest jedną transakcją. `OrderFulfillmentProcess`
jest zapisywany w PostgreSQL po każdym fakcie, ma optimistic version i deadline.
Po restarcie odtwarza ukończone kroki, a timeout publikuje przez outbox komendę
zwolnienia wcześniej zdobytego zasobu. Handlery Payment i Inventory wykonują
idempotentny lokalny efekt; `inventory.stock_reservations` przechowuje linie należące
do zamówienia. Kompensacja nadal może zawieść, dlatego pozostaje obserwowalna i
ponawialna. Dopiero `PaymentReleased`/`StockReleased` zamyka sagę jako `CANCELLED`;
wysłanie komendy samo w sobie nie jest sukcesem ani rollbackiem transakcji rozproszonej.

## Kontrakty API

REST jest domyślnym publicznym API. `POST /api/orders` obsługuje opcjonalny
`Idempotency-Key`; ponowienie identycznego requestu zwraca ten sam identyfikator,
a użycie klucza dla innego requestu daje `409`. `GET /api/orders/{id}` zwraca ETag
z wersji agregatu. Komenda anulowania może użyć `If-Match`; stara wersja daje `412`.
Błędy mają format `application/problem+json`.

Kontrakty:

- `docs/contracts/openapi.yaml` — publiczny REST;
- `src/main/resources/graphql/schema.graphqls` — elastyczny read model;
- `src/main/proto/ordering.proto` — wewnętrzne RPC;
- `docs/contracts/order-placed.md` — event integracyjny;
- `docs/contracts/protocols.md` — kryteria wyboru protokołu.

Resolver GraphQL i generowany z Protobuf serwis gRPC są celowymi **production
boundaries**, nie kopiami domeny. Adapter HTTP analizuje rzeczywisty AST i odrzuca
nadmierną głębokość/koszt przed resolverem. gRPC uruchamia osobny port po ustawieniu
`marketplace.grpc.enabled=true`; domyślnie nie zwiększa powierzchni runtime.
W profilu `secure` interceptor wymaga JWT, a komplet ścieżek
`marketplace.grpc.tls.{certificate,private-key,client-ca}` włącza mTLS. Kod domenowy
nie zna żadnego z protokołów.

`/ws/orders?orderId=<uuid>&after=<sequence>` jest sygnałem per zamówienie. Secure
handshake sprawdza właściciela zasobu. Komunikat ma rosnącą sekwencję, a krótki
bounded journal umożliwia wznowienie; starszy stan trzeba odczytać przez query API.

## CDC, replay i backfill

Outbox relay publikuje intencjonalne zdarzenia biznesowe. CDC (np. Debezium) czyta
log transakcyjny i dobrze nadaje się do integracji danych, lecz nie zastępuje nazwanego
kontraktu domenowego. Initial snapshot musi mieć punkt przejścia do streamu; konsument
zakłada duplikaty i możliwą zmianę kolejności.

`CdcProjectionPipeline` wykonuje initial snapshot z watermarkiem, buforuje live
traffic podczas snapshotu, utrzymuje checkpoint per partycja, deduplikuje offsety
i zatrzymuje tylko partycję z poison recordem. `ProjectionReconciler` wykrywa drift.
Replay i backfill zmieniają wyłącznie projekcję — **nie** uruchamiają płatności,
maili ani komend biznesowych.

`DebeziumOrderChangeAdapter` mapuje prawdziwy envelope i failuje przy drift schematu.
Osobna suite uruchamia PostgreSQL z logical WAL, Debezium Connect i Kafkę, dowodząc
przejścia snapshot (`op=r`) -> live update (`op=u`).

## Security

Threat model znajduje się w `docs/security/threat-model.md`. Przykłady security są
małe, ale wykonywalne: pokazują konkretną granicę, negatywny test i ograniczenie.
CORS kontroluje przeglądarkowe pochodzenie, CSRF chroni credentialed browser request,
a security headers ograniczają zachowanie klienta — te mechanizmy nie zastępują
uwierzytelnienia ani autoryzacji zasobu.

Profil `secure` uruchamia bezstanowy OAuth2 Resource Server: wszystkie endpointy
wymagają JWT, `/admin/**` wymaga scope `admin`, a `OrderOwnership` porównuje claim
`customer_id` z właścicielem zasobu również dla znanego z góry orderId (ochrona
przed IDOR). Lokalny profil jest jawnie otwarty. Uruchomienie zabezpieczone:

```powershell
$env:SPRING_PROFILES_ACTIVE='secure'
$env:JWT_SECRET='co-najmniej-32-znakowy-sekret-z-secret-managera'
.\gradlew.bat bootRun
```

Manifest Kubernetes celowo nie zawiera Secret. Przed wdrożeniem trzeba dostarczyć
`marketplace-secret` przez zewnętrzny secret manager/operator. Nie zapisuj tokenów,
sekretów, pełnego payloadu płatności ani PII w logach.

## Uruchomienie

Wymagania: Java 21 i Docker dla testów infrastrukturalnych.

```powershell
.\gradlew.bat bootRun
.\gradlew.bat test
.\gradlew.bat integrationTest
.\gradlew.bat cdcIntegrationTest
.\gradlew.bat clean check
docker compose up --build
```

Profile:

- bez profilu — szybkie adaptery in-memory;
- `postgres` — trwałe repozytoria i Flyway;
- `kafka` — prawdziwy broker;
- test integracyjny uruchamia PostgreSQL i Kafka przez Testcontainers.

Najważniejsze endpointy:

```text
POST /api/products
POST /api/stock
POST /api/orders
GET  /api/orders/{id}
POST /api/orders/{id}/cancel
GET  /api/admin/outbox
GET  /api/admin/kafka/dlq
GET  /actuator/health
```

## Testy

- `test` — domena, use case, architektura i deterministyczne laboratoria;
- `integrationTest` — Spring HTTP + prawdziwy PostgreSQL/Kafka;
- `cdcIntegrationTest` — PostgreSQL WAL + Debezium Connect + Kafka;
- CI uruchamia `cdcIntegrationTest` jako osobny kontrakt infrastrukturalny, dzięki czemu kosztowne laboratorium CDC nie wydłuża podstawowego joba weryfikacyjnego;
- test domeny dowodzi invariantu;
- test architektury dowodzi kierunku zależności;
- test kontraktu dowodzi kompatybilności payloadu;
- test integracyjny dowodzi semantyki infrastruktury, której fake nie odwzoruje.

Nie zwiększamy liczby testów dla pokrycia. Każdy test powinien odpowiadać na pytanie:
„jaką konkretną gwarancję utracimy, gdy ten test przestanie przechodzić?”.

## Deployment i incydenty

Docker uruchamia JVM jako PID 1, a Kubernetes ma startup/readiness/liveness probes,
grace period, nie-root/read-only filesystem, PDB i HPA. Readiness mówi, czy pod może
przyjmować ruch; liveness nie może zależeć od chwilowej awarii Kafki/PostgreSQL, bo
restart storm pogorszyłby incydent.

Canary nie jest promowany przy zbyt małej próbce. Polityka porównuje error rate i
p99 z limitem oraz baseline. Pełny runbook i game day: `docs/operations/canary-and-incident.md`.

## ADR

Decyzje architektoniczne znajdują się w `docs/adr`. ADR zapisuje kontekst, decyzję,
konsekwencje i alternatywy; nie jest instrukcją obsługi. Zmiana granic modułów,
strategii spójności albo protokołu wymaga nowego ADR zamiast cichej korekty historii.

## Świadome ograniczenia

- trwałe klucze idempotencji wymagają polityki retencji; laboratorium celowo nie
  usuwa ich automatycznie;
- ręczny adapter `KafkaConsumer` pozostaje deterministycznym laboratorium offsetów;
  główny runtime używa group coordination i manual ack przez `@KafkaListener`;
- schema GraphQL nie ma relacyjnych kolekcji, więc DataLoader będzie potrzebny przy
  ich dodaniu; AST budget już chroni koszt i głębokość;
- bounded journal WebSocket umożliwia krótkie wznowienie, ale nie zastępuje read modelu;
- model płatności uzgadnia `UNKNOWN` po provider operation ID; realny adapter musi
  przekazywać to ID także jako idempotency key providera;
- przykłady SSRF/upload nie zastępują egress proxy, malware scanning ani WAF;
- local Docker/Kubernetes manifests są laboratorium, nie kompletną platformą HA.

Te ograniczenia są częścią materiału: przykład nie udaje gwarancji, której nie daje.
