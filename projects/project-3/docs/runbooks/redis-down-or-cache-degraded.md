# Runbook — Redis down albo cache degraded

## Objaw

Spadek cache hit ratio, ostrzeżenia w logach albo wzrost latency Catalog Service/API Gateway.

## Metryki

```promql
sum(rate(app_cache_requests_total[5m])) by (cache, result)
```

```promql
sum(rate(http_server_requests_seconds_count{application="api-gateway",status="429"}[5m]))
```

## Logi

```logql
{service="catalog-service"} |= "Redis cache unavailable"
```

```logql
{service="api-gateway"} |= "Rate limiter Redis unavailable"
```

## Trace

Trace pokaże, że Catalog Service częściej trafia do DB zamiast kończyć szybko na cache.

## Reakcja

- Catalog Service powinien działać bez Redis, ale wolniej.
- API Gateway ma fail-open dla rate limitera — ruch nie powinien zostać zablokowany przez awarię Redis.
- Sprawdź, czy wzrost ruchu do DB nie powoduje wtórnej awarii.
