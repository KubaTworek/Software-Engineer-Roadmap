# Runbook — Rate limit spike

## Objaw

API Gateway zwraca dużo `429 Too Many Requests`.

## Metryki

```promql
sum(rate(http_server_requests_seconds_count{application="api-gateway",status="429"}[5m]))
```

## Logi

```logql
{service="api-gateway"} |= "http_request_completed" |= "429"
```

## Trace

Zwykle 429 kończy się na gatewayu i nie powinien generować downstream trace'ów.

## Reakcja

- Sprawdź, czy ruch pochodzi z jednego IP/API key.
- Jeśli to legalny partner, zwiększ limit dla API key.
- Jeśli to bot/abuse, zostaw limit i sprawdź downstream saturation.
- Jeśli Redis padł, gateway przechodzi w fail-open — wtedy 429 może spaść do zera, ale downstreamy są mniej chronione.
