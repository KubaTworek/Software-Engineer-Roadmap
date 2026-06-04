# Runbook — Payment dependency failure

## Objaw

Order Service zwraca zamówienia w stanie `PAYMENT_PENDING`, a circuit breaker `payment` jest otwarty.

## Metryki

```promql
resilience4j_circuitbreaker_state{name="payment"}
```

```promql
resilience4j_retry_calls_total{name="payment"}
```

```promql
histogram_quantile(0.95, sum(rate(http_client_requests_seconds_bucket[5m])) by (le, client_name))
```

## Logi

```logql
{service="order-service"} |= "order_degraded_to_payment_pending"
```

## Trace

W Tempo znajdź span do `payment-mock-service`. Sprawdź, czy problem to timeout, 5xx czy brak połączenia.

## Reakcja

1. Potwierdź, że Order Service degraduje bezpiecznie do `PAYMENT_PENDING`.
2. Sprawdź retry — jeżeli retry zwiększa obciążenie paymentów, zmniejsz liczbę prób.
3. Nie zamykaj circuit breakera ręcznie bez potwierdzenia poprawy dependency.
4. Po stabilizacji dependency sprawdź, czy half-open wraca do closed.
