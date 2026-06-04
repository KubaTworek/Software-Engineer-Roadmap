# Runbook — Error rate spike

## Objaw

Wzrost 5xx w jednym lub kilku serwisach.

## Metryki

```promql
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application, uri)
```

```promql
sum(rate(http_server_requests_seconds_count[5m])) by (application, uri, status)
```

## Logi

```logql
{level="WARN"} |= "request_failed"
```

Po correlation ID:

```logql
{correlationId="<correlation-id>"}
```

## Trace

W Tempo sprawdź, czy błąd powstaje w serwisie źródłowym, czy w downstreamie.

## Reakcja

- Jeśli błąd pochodzi z dependency: sprawdź circuit breaker.
- Jeśli błąd pochodzi z walidacji biznesowej, nie traktuj go jako incident platformowy.
- Jeśli 5xx rośnie razem z latency, sprawdź saturation.
- Jeśli 5xx rośnie po deployu, wykonaj rollback.
