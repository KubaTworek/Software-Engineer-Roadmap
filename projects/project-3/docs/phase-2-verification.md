# Phase 2 verification: mikroserwisy lokalnie

Ten dokument sprawdza, czy faza 2 jest domknięta funkcjonalnie, a nie tylko katalogowo.

## Zakres fazy 2

Faza 2 obejmuje:

- osobne aplikacje dla gateway, catalog, reservation, order, payment mock i notification,
- komunikację HTTP między serwisami,
- RabbitMQ dla eventu `order.paid`,
- osobne bazy PostgreSQL dla catalog, reservation i order,
- Redis dla cache katalogu,
- propagację `X-Correlation-Id` i `X-Request-Id`,
- uruchamianie infrastruktury przez Docker Compose,
- opcjonalne uruchomienie aplikacji przez profil Compose `apps`,
- podstawowy test przepływu end-to-end.

## Uruchomienie lokalne: aplikacje z IDE, infrastruktura z Dockera

```bash
cp .env.example .env
docker compose up -d catalog-db reservation-db order-db redis rabbitmq prometheus grafana tempo
mvn clean package
```

Następnie uruchom aplikacje z IDE albo terminala:

```bash
mvn -pl catalog-service spring-boot:run
mvn -pl reservation-service spring-boot:run
mvn -pl payment-mock-service spring-boot:run
mvn -pl order-service spring-boot:run
mvn -pl notification-service spring-boot:run
mvn -pl api-gateway spring-boot:run
```

## Uruchomienie całości przez Docker Compose

Najpierw zbuduj JAR-y:

```bash
mvn clean package
```

Potem uruchom całość:

```bash
docker compose --profile apps up --build
```

## Ręczny test happy path

### 1. Lista wydarzeń

```bash
curl -i http://localhost:8080/events \
  -H "X-Correlation-Id: demo-correlation-1"
```

### 2. Utworzenie rezerwacji

```bash
curl -i -X POST http://localhost:8080/reservations \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: demo-correlation-2" \
  -d '{
    "eventId": "11111111-1111-1111-1111-111111111111",
    "userId": "kubat",
    "quantity": 1
  }'
```

Zapisz `id` z odpowiedzi jako `RESERVATION_ID`.

### 3. Utworzenie zamówienia

```bash
curl -i -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: demo-correlation-3" \
  -H "Idempotency-Key: demo-order-1" \
  -d '{
    "reservationId": "RESERVATION_ID",
    "userId": "kubat"
  }'
```

Oczekiwane statusy:

- `PAID` — płatność się udała, rezerwacja została potwierdzona, event poszedł do RabbitMQ,
- `PAYMENT_PENDING` — Payment Mock zwrócił błąd lub timeout; to będzie wejście do Fazy 3.

### 4. Sprawdzenie rezerwacji

```bash
curl -i http://localhost:8080/reservations/RESERVATION_ID \
  -H "X-Correlation-Id: demo-correlation-4"
```

Po udanej płatności status powinien być `CONFIRMED`.

## k6 happy path

```bash
k6 run load-tests/happy-path.js
```

## Porty

| Komponent | Port |
|---|---:|
| API Gateway | 8080 |
| Catalog Service | 8081 |
| Reservation Service | 8082 |
| Order Service | 8083 |
| Payment Mock Service | 8084 |
| Notification Service | 8085 |
| RabbitMQ | 5672 |
| RabbitMQ UI | 15672 |
| Prometheus | 9090 |
| Grafana | 3000 |
| Tempo | 3200 |

## Kryterium domknięcia fazy 2

Faza 2 jest domknięta, gdy możesz pokazać działający przepływ:

```text
Gateway → Catalog
Gateway → Reservation → Catalog
Gateway → Order → Reservation
Gateway → Order → Payment Mock
Order → RabbitMQ → Notification
```

To nadal nie jest jeszcze pełna Faza 3. Circuit breaker, retry, degradacja, rate limiting i cache stampede będą następne.
