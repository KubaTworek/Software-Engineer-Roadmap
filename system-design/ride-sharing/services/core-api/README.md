# Ride Sharing — Core API

Właściciel transakcyjnego cyklu życia przejazdu, kierowcy, płatności i trwałych zdarzeń. Usługi skalujące nie mogą samodzielnie zmieniać stanu agregatu `Ride`.

| Pytanie | Odpowiedź |
| --- | --- |
| Najważniejszy niezmiennik | Jeden kierowca ma najwyżej jedną aktywną ofertę/przejazd, a status ride przechodzi wyłącznie po krawędziach maszyny stanów. |
| Krytyczny przepływ | idempotent request → commit ride/outbox → autoryzacja po commicie → matching → oferta z deadline → accept → trip → capture po commicie. |
| Rekomendowany test | `RideStateMachineTest`, `DriverOfferOwnershipTest`; z Dockerem `PostgreSqlRideVersionInfrastructureTest`. |
| Świadome ograniczenia | Płatność jest mockowana, lokalny profil używa jednego indeksu Redis GEO, a saga wymaga produkcyjnego procesu reconciliation. |

## Zakres implementacji

Dodane elementy:

- idempotency keys dla krytycznych operacji, szczególnie `POST /api/v1/rides`,
- outbox pattern dla niezawodnej publikacji eventów,
- Kafka jako event bus,
- monitoring przez Spring Boot Actuator, Micrometer i Prometheus,
- retry/circuit breaker dla integracji z mapami i płatnościami,
- lepsza state machine przejazdu z historią przejść statusów,
- proste API admin panelu,
- support tools i obsługa ticketów.

Core pozostaje modularnym właścicielem danych transakcyjnych. Profil domyślny uruchamia lokalne adaptery lokalizacji i pricingu. Profil `distributed` zastępuje je klientami HTTP bez zmiany kodu domenowego.

## Stack

- Java 21
- Spring Boot 3.5.14
- Spring Security + JWT
- PostgreSQL + Flyway
- Redis GEO
- Kafka
- Kafka + osobny `realtime-gateway`
- Resilience4j
- Micrometer + Prometheus
- Docker Compose

## Uruchomienie

```powershell
..\..\mvnw.cmd -pl services/core-api test
..\..\mvnw.cmd -pl services/core-api spring-boot:run
```

Test kontraktów PostgreSQL:

```powershell
..\..\mvnw.cmd -pl services/core-api -Pinfrastructure-tests verify
```

Endpointy:

- API: `http://localhost:8080`
- Actuator health: `http://localhost:8080/actuator/health`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus`
- Prometheus UI: `http://localhost:9090`

## Idempotency Keys

Krytyczne requesty powinny używać nagłówka:

```http
Idempotency-Key: passenger-123-create-ride-001
```

Przykład:

```http
POST /api/v1/rides
Authorization: Bearer <token>
Idempotency-Key: passenger-123-create-ride-001
Content-Type: application/json
```

Jeżeli klient ponowi identyczny request z tym samym kluczem, system zwróci tę samą odpowiedź. Jeżeli użyje tego samego klucza z innym body, API zwróci konflikt.

## Outbox Pattern

Zamiast publikować event bezpośrednio w transakcji biznesowej, aplikacja zapisuje pełny `DomainEventEnvelope` w tabeli `outbox_events`. Instancja publishera claimuje krótką paczkę przez `FOR UPDATE SKIP LOCKED`, zwalnia transakcję przed I/O do Kafki, a potwierdzenie zapisuje osobno. Crash po wysłaniu, ale przed ackiem może utworzyć duplikat — dlatego `eventId` jest stabilne, a consumer musi być idempotentny.

Obsługiwane topic-i:

- `ride.events`
- `payment.events`
- `support.events`

## Ride State Machine

Przejścia statusów są walidowane przez `RideStateMachine`.

Najważniejszy flow:

```text
REQUESTED -> MATCHING -> DRIVER_ASSIGNED -> DRIVER_ARRIVING -> DRIVER_ARRIVED -> IN_PROGRESS -> COMPLETED
```

Anulowania i błędy są kontrolowane przez dozwolone przejścia. Każda zmiana trafia do `ride_status_history`.

## Admin API

Wymaga roli `ADMIN`.

```http
GET /api/v1/admin/overview
GET /api/v1/admin/users
GET /api/v1/admin/drivers
GET /api/v1/admin/rides?status=MATCHING
GET /api/v1/admin/rides/{rideId}/history
POST /api/v1/admin/rides/{rideId}/force-cancel
GET /api/v1/admin/support/tickets?status=OPEN
```

## Support API

Dostępne dla zalogowanych użytkowników:

```http
POST /api/v1/support/tickets
GET /api/v1/support/tickets/me
```

Dostępne dla admina:

```http
GET /api/v1/support/tickets
PATCH /api/v1/support/tickets/{ticketId}
```

## Resilience

Integracje z mapami i płatnościami są opakowane przez Resilience4j:

- retry,
- circuit breaker,
- fallback.

Konfiguracja znajduje się w `application.yml`.

## Ważna uwaga

To laboratorium nie obiecuje distributed transaction. Autoryzacja i capture są uruchamiane po commicie, a połączenie zapisów zapewnia saga oraz idempotentne komendy providera. Produkcyjnie potrzebne są timeouty/deadline, kolejka trwałych komend płatniczych, reconciliation, DLQ, rotacja sekretów i contract tests adapterów HTTP.
