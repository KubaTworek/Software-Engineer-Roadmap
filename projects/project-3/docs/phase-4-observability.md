# Faza 4 — Observability

Ta faza domyka diagnostykę systemu. Celem nie jest samo dodanie Prometheusa albo Grafany, tylko możliwość przejścia od objawu do przyczyny przy użyciu trzech źródeł dowodów:

1. **metryka** — potwierdza, że problem istnieje i określa jego skalę,
2. **log strukturalny** — pokazuje kontekst techniczny i biznesowy,
3. **trace** — pokazuje, który downstream, baza albo broker odpowiada za latency lub błąd.

## Co zostało dodane

- structured JSON logs przez `logback-spring.xml` w każdym serwisie,
- `traceId`, `spanId`, `correlationId`, `requestId` w logach,
- request logging filter w module `common`,
- Prometheus scrape dla wszystkich serwisów,
- reguły alertów Prometheus,
- Grafana datasource: Prometheus, Tempo, Loki,
- dashboard `Ticketing Platform - Overview`,
- OpenTelemetry przez Micrometer Tracing + OTLP exporter,
- Tempo jako backend trace'ów,
- opcjonalny Jaeger UI pod profilem `observability-extra`,
- Loki + Promtail do zbierania logów kontenerów,
- runbooki dla najważniejszych awarii.

## Endpointy

| Narzędzie | URL |
|---|---|
| API Gateway | `http://localhost:8080` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3000` |
| Tempo | `http://localhost:3200` |
| Loki | `http://localhost:3100` |
| RabbitMQ Management | `http://localhost:15672` |
| Jaeger UI, opcjonalnie | `http://localhost:16686` |

Grafana login lokalny:

```text
admin / admin
```

## Uruchomienie

```bash
docker compose --profile apps up --build
```

Opcjonalnie z Jaegerem:

```bash
docker compose --profile apps --profile observability-extra up --build
```

Domyślnym backendem trace'ów jest Tempo:

```text
MANAGEMENT_OTLP_TRACING_ENDPOINT=http://tempo:4318/v1/traces
```

Jeżeli chcesz wysyłać trace'y do Jaegera zamiast Tempo, ustaw endpoint na:

```text
http://jaeger:4318/v1/traces
```

## Jak diagnozować awarię

### 1. Zacznij od metryki

Przykłady:

```promql
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application))
```

```promql
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application)
```

```promql
resilience4j_circuitbreaker_state
```

```promql
sum(rate(app_cache_requests_total[5m])) by (cache, result)
```

### 2. Przejdź do trace'a

W Grafanie otwórz Tempo i wyszukaj trace po `traceId`. Trace powinien pokazać przepływ:

```text
api-gateway
  -> order-service
    -> reservation-service
    -> payment-mock-service
    -> rabbitmq
      -> notification-service
```

### 3. Potwierdź logami

Logi mają format JSON. Najważniejsze pola:

```json
{
  "service": "order-service",
  "traceId": "...",
  "spanId": "...",
  "correlationId": "...",
  "requestId": "...",
  "method": "POST",
  "path": "/orders",
  "status": "200",
  "durationMs": "123",
  "message": "order_degraded_to_payment_pending ..."
}
```

W Loki możesz filtrować np.:

```logql
{service="order-service"} |= "order_degraded_to_payment_pending"
```

albo po correlation ID:

```logql
{correlationId="<correlation-id>"}
```

## Alerty

Reguły alertów są w:

```text
observability/prometheus/rules/alerts.yml
```

Obecne alerty:

- `HighOrderServiceP95Latency`,
- `HighReservationErrorRate`,
- `PaymentCircuitBreakerOpen`,
- `DatabaseConnectionPoolSaturation`,
- `RedisCacheHitRatioDropped`,
- `RateLimitRejectionsSpike`,
- `NotificationConsumerLagSuspected`.

## Kryteria ukończenia Fazy 4

Faza 4 jest ukończona, gdy dla typowej awarii potrafisz pokazać:

1. alert albo wykres, który wykrył problem,
2. konkretny trace pokazujący wolny/błędny downstream,
3. logi po `traceId` albo `correlationId`, które potwierdzają przyczynę,
4. runbook opisujący reakcję.
