# Runbook — High latency

## Objaw

Order Service albo inny serwis ma wysokie p95/p99 latency.

## Metryki

```promql
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application, uri))
```

```promql
sum(rate(http_server_requests_seconds_count[5m])) by (application, uri, status)
```

## Trace

1. Wejdź do Grafany.
2. Otwórz Tempo.
3. Wyszukaj trace po `traceId` z logu albo wybierz wolny trace.
4. Sprawdź najdłuższy span: payment, reservation, catalog, DB, Redis lub RabbitMQ.

## Logi

```logql
{service="order-service"} |= "http_request_completed"
```

Po znalezieniu `correlationId`:

```logql
{correlationId="<correlation-id>"}
```

## Reakcja

- Jeżeli wolny jest Payment Service: sprawdź circuit breaker i degradację do `PAYMENT_PENDING`.
- Jeżeli wolna jest baza: sprawdź Hikari pool, liczbę aktywnych połączeń i wolne zapytania.
- Jeżeli wolny jest Redis: sprawdź cache hit ratio i logi `Redis cache unavailable`.
- Jeżeli problem dotyczy tylko jednego endpointu: ogranicz ruch albo dodaj cache/degradację.

## Czego nie robić

- Nie zwiększaj instancji bez potwierdzenia bottlenecku.
- Nie zwiększaj timeoutów jako pierwszej reakcji.
- Nie dodawaj retry bez sprawdzenia, czy nie wzmacnia awarii.
