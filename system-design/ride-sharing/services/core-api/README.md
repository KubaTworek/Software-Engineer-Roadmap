# Ride Sharing — Stage 2 Stabilization

Backend Java/Spring Boot rozszerzający MVP Ride Sharing o elementy stabilizacji produkcyjnej.

## Zakres Etapu 2

Dodane elementy:

- idempotency keys dla krytycznych operacji, szczególnie `POST /api/v1/rides`,
- outbox pattern dla niezawodnej publikacji eventów,
- Kafka jako event bus,
- monitoring przez Spring Boot Actuator, Micrometer i Prometheus,
- retry/circuit breaker dla integracji z mapami i płatnościami,
- lepsza state machine przejazdu z historią przejść statusów,
- proste API admin panelu,
- support tools i obsługa ticketów.

Projekt nadal jest modularnym monolitem. To rozsądny etap przed wydzielaniem mikroserwisów.

## Stack

- Java 21
- Spring Boot 3.3.x
- Spring Security + JWT
- PostgreSQL + Flyway
- Redis GEO
- Kafka
- WebSocket/STOMP
- Resilience4j
- Micrometer + Prometheus
- Docker Compose

## Uruchomienie

```bash
docker compose up -d postgres redis zookeeper kafka
mvn spring-boot:run
```

Albo z aplikacją w kontenerze:

```bash
docker compose --profile app up --build
```

Monitoring:

```bash
docker compose --profile observability up -d prometheus grafana
```

Endpointy:

- API: `http://localhost:8080`
- Actuator health: `http://localhost:8080/actuator/health`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus`
- Prometheus UI: `http://localhost:9090`
- Grafana: `http://localhost:3000`

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

Zamiast publikować event bezpośrednio w transakcji biznesowej, aplikacja zapisuje rekord w tabeli `outbox_events`. Scheduler `OutboxPublisher` publikuje eventy do Kafki i oznacza je jako `PUBLISHED`.

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

To nadal nie jest pełny system produkcyjny. Brakuje między innymi pełnego UI admina, dokładnego audytu uprawnień supportu, dead-letter topiców, rozbudowanego reconciliation dla płatności i testów kontraktowych. Kod jest jednak przygotowany jako sensowna baza do kolejnego etapu.
