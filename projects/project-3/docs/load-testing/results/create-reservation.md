# Load test result — create reservation

## Command

```bash
k6 run load-tests/create-reservation.js
```

## Założenie

TODO: Opisz hipotezę przed testem. Przykład: przy rozgrzanym cache endpoint powinien utrzymać p95 poniżej 500 ms.

## Wynik

TODO: Wklej najważniejsze dane z k6: RPS, p95, p99, error rate, checks rate.

## Bottleneck

TODO: Wskaż pierwszy element, który ograniczył system. Nie zgaduj — oprzyj się na metrykach.

## Metryka potwierdzająca

TODO: Podaj konkretną metrykę Prometheus/Grafana, log albo trace.

Przykłady:

- `http_server_requests_seconds_bucket{application="order-service"}`
- `resilience4j_circuitbreaker_state`
- `app_cache_requests_total`
- `hikaricp_connections_active`
- RabbitMQ queue depth dla `notifications.order-paid`

## Logi / trace

TODO: Wpisz `correlationId`, `traceId` albo fragment logu potwierdzający diagnozę.

## Zmiana

TODO: Opisz zmianę po pierwszym przebiegu, np. większy pool DB, dłuższy timeout, mniejszy retry, cache TTL, więcej consumerów.

## Wynik po zmianie

TODO: Porównaj p95, p99, error rate i koszt.

## Trade-off

TODO: Opisz koszt decyzji. Przykład: większy cache zmniejsza latency, ale zwiększa ryzyko nieświeżych danych i koszt Redis.
