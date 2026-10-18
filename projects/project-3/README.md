# Microservices Ticketing Platform

Project 3 odpowiada Stage 3 z `backend-engineering`. Pokazuje nie liczbę usług,
lecz zachowanie systemu rozproszonego podczas współbieżności, częściowej awarii,
przeciążenia i odtwarzania po katastrofie.

Pierwotna specyfikacja i pytania do samodzielnego przejścia projektu są zachowane
w [`docs/project-brief.md`](docs/project-brief.md). Ten plik opisuje wyłącznie
aktualny system oraz dowody jego gwarancji.

## Czego dowodzi projekt

| Gwarancja | Mechanizm | Najlepszy dowód | Granica produkcyjna |
| --- | --- | --- | --- |
| brak oversellingu | inventory należące do Reservation Service, atomowy SQL | `PostgreSqlInventoryConcurrencyTest` | zmiana pojemności wymaga osobnego kontraktu administracyjnego |
| stary worker nie nadpisze nowego | PostgreSQL lease i fencing token w fenced resource | scenariusz dwóch właścicieli w `PostgreSqlInventoryConcurrencyTest` | sam lease nie zapewnia bezpieczeństwa |
| bezpieczny retry płatności | stabilny `Idempotency-Key`, lookup, kompensacja, `PAYMENT_UNKNOWN` i trwały ledger | `OrderEntityTest`, `PaymentPersistenceInfrastructureTest`, smoke test po restarcie | mock nie modeluje settlementu, chargebacku i webhooków providera |
| brak luki DB → broker | transactional outbox, publisher confirm i terminalny poison record | `OutboxMessageTest`, `PostgreSqlOutboxInfrastructureTest` | delivery pozostaje at-least-once |
| brak podwójnego efektu konsumenta | trwały inbox marker i sterowane potwierdzenie wiadomości | `RabbitDeadLetterInfrastructureTest` | zewnętrzny provider powiadomień również musi być idempotentny |
| kontrolowane przeciążenie | propagowany deadline, osobne bulkheady i load shedding | `DeadlineAndAdmissionTest`, test Redis i k6 | limity trzeba skalibrować z capacity downstreamu |
| diagnozowalność awarii | correlation ID, trace context, metryki RED/USE, alerty i runbooki | dashboardy, `operational-drill` i zweryfikowany raport | telemetria nie zastępuje poprawnego modelu domenowego |

Pełna mapa kod → test → metryka → ograniczenie znajduje się w
[`docs/stage-3-guarantees.md`](docs/stage-3-guarantees.md).

## Architektura

```text
Client
  -> API Gateway
       -> Catalog Service ---------> catalog PostgreSQL + Redis
       -> Reservation Service -----> reservation PostgreSQL
              -> Order Service ----> order PostgreSQL
                     -> Payment Mock Service -> payment PostgreSQL
                     -> transactional outbox -> RabbitMQ
                                                   -> Notification Service
```

Każda usługa posiada własne dane. Wywołania synchroniczne przenoszą deadline,
correlation ID i trace context. Zdarzenia asynchroniczne używają stabilnego
`messageId`, outboxa oraz inboxa. Redis jest optymalizacją ścieżki odczytu, a nie
źródłem prawdy.

Moduł `common` zawiera wyłącznie mechanizmy platformowe i dwa jawne kontrakty
zdarzeń. Jest to świadomy kompromis edukacyjny: wspólna biblioteka upraszcza
lokalne laboratorium, ale tworzy build-time coupling. W niezależnie wydawanych
usługach kontrakty powinny być wersjonowanym schematem, a producent i konsument
powinni posiadać własne modele oraz test kompatybilności.

Szczegóły granic usług i przepływów opisuje
[`docs/architecture.md`](docs/architecture.md).

## Moduły

| Moduł | Odpowiedzialność |
| --- | --- |
| `api-gateway` | routing, deadline, correlation, rate limiting i odrzucenie przeciążenia |
| `catalog-service` | katalog wydarzeń, cache-aside i degradacja odczytu |
| `reservation-service` | właściciel inventory, rezerwacja, lease i fencing |
| `order-service` | saga zamówienia, płatność, kompensacja, outbox i rekoncyliacja |
| `payment-mock-service` | idempotentny, trwały ledger oraz sterowane awarie zależności |
| `notification-service` | konsument RabbitMQ, inbox, retry i DLQ |
| `common` | jawnie współdzielone mechanizmy platformowe i kontrakty laboratorium |

## Najkrótsza ścieżka uruchomienia

Wymagania: Java 21, Docker oraz Docker Compose. Maven Wrapper przypina Maven 3.9.9.

```powershell
# szybkie testy bez kontenerów
.\mvnw.cmd --batch-mode --no-transfer-progress verify

# kontrakty PostgreSQL, Redis i RabbitMQ
.\mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify

# pełny system lokalny
docker compose --profile apps up --detach --build --wait
.\scripts\smoke-test.ps1

# recovery i krótki drill operacyjny
.\scripts\local-restore-drill.ps1
.\scripts\operational-drill.ps1
```

Na Linux/macOS odpowiednikami są skrypty `.sh` w katalogu `scripts`.

## Warstwy weryfikacji

| Warstwa | Co udowadnia | Komenda |
| --- | --- | --- |
| szybkie testy | maszyny stanów, deadline, admission control i modele domenowe | `./mvnw verify` |
| infrastruktura | semantykę blokad, outboxa, Redis, RabbitMQ i trwałości płatności | `./mvnw -Pinfrastructure-tests verify` |
| system smoke | pełny przepływ rezerwacja → zamówienie → płatność → powiadomienie oraz restart | `./scripts/smoke-test.sh` |
| eksperyment | zachowanie pod ruchem i podczas kontrolowanej awarii | `./scripts/operational-drill.sh` |
| IaC | składnię, zależności i statyczne reguły wdrożenia AWS | `terraform validate` w `infra/aws/terraform` |

Test pominięty z powodu braku Dockera nie jest dowodem infrastruktury. Profil CI
`infrastructure-contracts` uruchamia te kontrakty osobno, a `system-workflow`
weryfikuje cały system.

## Observability i operacje

Lokalny stack zawiera Prometheus, Grafanę, Jaegera oraz OpenTelemetry Collector.
Najważniejsze materiały:

- [`docs/phase-4-observability.md`](docs/phase-4-observability.md) — metryki, logi i tracing;
- [`docs/runbooks.md`](docs/runbooks.md) — indeks procedur incydentowych;
- [`docs/operations/experiment-policy.md`](docs/operations/experiment-policy.md) — zasady eksperymentów;
- [`docs/operations/verified-local-drill.md`](docs/operations/verified-local-drill.md) — rzeczywisty wynik lokalnej weryfikacji;
- [`docs/disaster-recovery.md`](docs/disaster-recovery.md) — RPO, RTO, backup, restore i failover;
- [`docs/phase-6-cloud-deployment.md`](docs/phase-6-cloud-deployment.md) — granice wdrożenia cloud, koszt i capacity;
- [`infra/aws/terraform/README.md`](infra/aws/terraform/README.md) — uruchomienie i granice modelu IaC.

Szablony raportów obciążeniowych znajdują się w
`docs/load-testing/templates`. Katalog `results` jest przeznaczony wyłącznie na
wyniki z konkretną datą, konfiguracją, metrykami i wnioskiem.

## Bezpieczeństwo

Project 3 zakłada mechanizmy uwierzytelniania i autoryzacji przećwiczone w Project
2, dlatego lokalny gateway nie implementuje kolejnego laboratorium JWT. Tutaj nową
granicą są workload identity, minimalne IAM, prywatna sieć, zarządzanie sekretami
i brak długowiecznych danych dostępowych w obrazach.

To świadome ograniczenie, nie placeholder. Przed publicznym wdrożeniem gateway musi
weryfikować tożsamość klienta, a usługi — tożsamość workloadu i uprawnienia do
konkretnego zasobu. Model cloud opisuje te odpowiedzialności w
[`docs/phase-6-cloud-deployment.md`](docs/phase-6-cloud-deployment.md) i Terraform.

## Wersje i zależności

- Java 21;
- Spring Boot 4.0.3;
- Maven 3.9.9 przez wrapper;
- Testcontainers 1.21.4;
- wersje obrazów kontenerowych są przypięte, bez `latest`.

Project 1–3 używają wspólnej bazowej generacji Javy, Spring Boota i Testcontainers.
Zmianę wersji należy wykonywać osobnym PR-em wraz z pełnym buildem, testami
infrastrukturalnymi i systemowym smoke testem.

## Granice projektu

- Payment Service jest kontrolowanym providerem, nie systemem rozliczeniowym.
- Auth z Project 2 nie jest kopiowany; lokalny gateway nie powinien być wystawiany
  publicznie bez dodania tej granicy.
- RabbitMQ reprezentuje broker at-least-once; wybór Kafka/SQS zmieniłby część
  kontraktów operacyjnych.
- Terraform jest wykonywalnym modelem decyzji, ale produkcja wymaga zdalnego state,
  polityk organizacji, domeny, certyfikatów i pipeline'u wdrożeniowego.
- Szablon eksperymentu nie jest wynikiem. Wniosek wymaga zapisanych parametrów,
  p95/p99, error rate, saturacji i korelacji z logiem lub trace'em.

Projekt jest kompletny w zakresie Stage 3, gdy jego gwarancje można nie tylko
opisać, lecz również odtworzyć odpowiednią komendą i wskazać granicę, poza którą
dany mechanizm już nie wystarcza.
