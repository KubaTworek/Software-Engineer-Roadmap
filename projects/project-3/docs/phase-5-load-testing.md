# Faza 5 — Load testing

Ta faza dodaje praktyczny zestaw testów obciążeniowych opartych o k6. Celem nie jest tylko wygenerowanie ruchu, ale nauczenie się diagnozowania systemu na podstawie danych: metryk, logów i trace'ów.

Każdy scenariusz ma odpowiedzieć na siedem pytań:

```text
Założenie:
Wynik:
Bottleneck:
Metryka potwierdzająca:
Zmiana:
Wynik po zmianie:
Trade-off:
```

## Wymagania

Lokalnie:

```bash
docker compose --profile apps up --build
k6 version
```

Albo przez Dockera:

```bash
docker compose --profile apps --profile load-tests up --build
```

## Szybkie uruchomienie

```bash
./scripts/run-load-tests.sh
```

Scenariusz Redis-down wymaga zatrzymania Redis, więc jest osobnym skryptem:

```bash
./scripts/redis-down-test.sh
```

Reset ustawień chaosowych:

```bash
./scripts/reset-chaos.sh
```

## Scenariusze

| Scenariusz | Plik | Co sprawdza |
|---|---|---|
| Browse events | `load-tests/browse-events.js` | cache, read path, latency p95/p99 |
| Create reservation | `load-tests/create-reservation.js` | write path, catalog dependency, DB writes |
| Create order | `load-tests/create-order.js` | pełny przepływ order → reservation → payment → broker |
| Payment timeout | `load-tests/payment-timeout.js` | timeout, retry/backoff, circuit breaker, `PAYMENT_PENDING` |
| Payment errors | `load-tests/payment-errors.js` | błędy downstream, circuit breaker, graceful degradation |
| Redis down | `load-tests/redis-down.js` | fallback cache do DB i fail-open rate limitera |
| DB slow | `load-tests/db-slow.js` | wpływ wolnej bazy na p95/p99 i downstreamy |
| Broker lag | `load-tests/broker-lag.js` | asynchroniczność powiadomień, queue depth, consumer lag |
| Traffic spike | `load-tests/traffic-spike.js` | nagły wzrost ruchu read/write, saturacja i limity |

## Fault injection

Dodałem kontrolowane endpointy chaosowe tylko do środowiska treningowego. W realnym systemie nie wystawiałbyś ich publicznie.

### Payment chaos

```bash
curl -X POST http://localhost:8084/internal/chaos/payment \
  -H 'Content-Type: application/json' \
  -d '{"failureRate":0.75,"maxDelayMs":300}'

curl -X POST http://localhost:8084/internal/chaos/payment/reset
```

### Catalog DB delay

```bash
curl -X POST http://localhost:8081/internal/chaos/catalog/db-delay \
  -H 'Content-Type: application/json' \
  -d '{"delayMs":1200}'

curl -X POST http://localhost:8081/internal/chaos/catalog/reset
```

### Notification lag

```bash
curl -X POST http://localhost:8085/internal/chaos/notification/processing-delay \
  -H 'Content-Type: application/json' \
  -d '{"delayMs":2000}'

curl -X POST http://localhost:8085/internal/chaos/notification/reset
```

### Redis down

```bash
docker compose stop redis
k6 run load-tests/redis-down.js
docker compose start redis
```

## Jak analizować wyniki

Po każdym teście sprawdź:

1. k6: `http_req_duration`, `http_req_failed`, `checks`, throughput.
2. Prometheus: latency, error rate, saturation, retry count, circuit breaker state, cache hit/miss.
3. Grafana: dashboard system overview.
4. Loki/logi: wyszukiwanie po `correlationId`, `traceId`, `orderId`.
5. Tempo/Jaeger: trace konkretnego wolnego requestu.
6. RabbitMQ: queue depth `notifications.order-paid`.

Nie wpisuj w raporcie „chyba baza była wolna”. Wpisz metrykę i trace, które to pokazują.

## Oczekiwane obserwacje

### Browse events

Przy rozgrzanym cache latency powinno być wyraźnie niższe, a DB mniej obciążona. Jeżeli cache hit ratio spada, p95 może rosnąć.

### Create reservation

Bottleneckiem zwykle będzie DB rezerwacji albo zależność do katalogu. Warto patrzeć na latency `reservation-service` oraz połączenia JDBC.

### Create order

Najbardziej wrażliwy element to płatność. Payment timeout lub błędy powinny prowadzić do degradacji `PAYMENT_PENDING`, a nie do kaskadowej awarii całego endpointu.

### Redis down

Read path powinien nadal działać z DB fallbackiem, ale drożej i wolniej. Gateway rate limiter jest skonfigurowany jako fail-open, więc system przepuszcza ruch, zamiast odcinać wszystkich użytkowników przez awarię Redis.

### DB slow

p95/p99 wzrośnie. Trace powinien pokazać wolny span w `catalog-service`. Logi powinny zawierać `catalog_database_delay_simulated`.

### Broker lag

Endpoint zamówienia nie powinien czekać na wysłanie powiadomienia. Lag powinien być widoczny w RabbitMQ queue depth i ewentualnych alertach.

### Traffic spike

Tu szukasz pierwszego elementu, który się nasyca: gateway, DB pool, payment dependency, RabbitMQ albo rate limiter.

## Pliki raportowe

Każdy test generuje summary do:

```text
load-tests/reports/*.summary.json
load-tests/reports/*.summary.md
```

Dodatkowo wypełnij ręcznie szablony w:

```text
docs/load-testing/results/
```

To jest ważniejsze niż sam wynik k6, bo uczy argumentowania decyzji technicznych.
