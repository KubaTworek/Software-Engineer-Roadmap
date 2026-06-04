# Phase 2 completion report

## Status

Faza 2 została domknięta jako działający lokalny szkielet mikroserwisów. Nie jest to jeszcze produkcyjny system, ale zawiera realne granice serwisów, HTTP między serwisami, RabbitMQ, osobne bazy danych i podstawowy przepływ end-to-end.

## Zaimplementowany przepływ

```text
Client
  -> API Gateway
    -> Catalog Service -> PostgreSQL + Redis cache
    -> Reservation Service -> Catalog Service -> PostgreSQL
    -> Order Service -> Reservation Service
                    -> Payment Mock Service
                    -> RabbitMQ
                       -> Notification Service
```

## Co zostało dodane względem pierwszego szkieletu

- `ReservationService` waliduje dostępność eventu przez `CatalogService`.
- `OrderService` pobiera rezerwację przez `ReservationService` przed płatnością.
- `OrderService` po udanej płatności potwierdza rezerwację przez `ReservationService`.
- `OrderService` publikuje `OrderPaidEvent` do RabbitMQ.
- `NotificationService` konsumuje event `order.paid`.
- `X-Correlation-Id` i `X-Request-Id` są propagowane przez gateway i klientów WebClient.
- Wszystkie serwisy mają porty, konfigurację środowiskową i actuator metrics.
- Docker Compose ma profil `apps`, który pozwala uruchomić także aplikacje w kontenerach po zbudowaniu JAR-ów.
- Dodano `docs/phase-2-verification.md` z ręcznym testem.
- Dodano `scripts/smoke-test.sh`.
- Dodano `load-tests/happy-path.js`.

## Ograniczenia świadomie zostawione na Fazę 3

- Brak pełnej ochrony przed oversellingiem na poziomie atomowego inventory.
- Brak rate limitingu w gateway.
- Brak pełnej konfiguracji timeoutów dla wszystkich klientów HTTP.
- Brak dopracowanych fallbacków biznesowych.
- Brak retry/circuit breaker poza szkieletem dla płatności.
- Brak cache stampede protection i single-flight.
- Brak pełnych dashboardów Grafana.

To są właściwe tematy na Fazę 3: resilience, degradacja, metryki i testy awarii.
