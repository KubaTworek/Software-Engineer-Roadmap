# Runbook — Payment dependency failure

## Objaw

Rośnie liczba zamówień `PAYMENT_UNKNOWN`, `COMPENSATION_PENDING` lub `MANUAL_REVIEW`,
a circuit breaker `payment` jest otwarty.

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
{service="order-service"} |~ "payment_unknown|order_reservation_confirmation_pending"
```

## Trace

W Tempo znajdź span do `payment-mock-service`. Sprawdź, czy problem to timeout, 5xx czy brak połączenia.

## Reakcja

1. Potwierdź, że Order Service używa lookupu i nie zamienia timeoutu automatycznie w `FAILED`.
2. Sprawdź retry — jeżeli retry zwiększa obciążenie paymentów, zmniejsz liczbę prób.
3. Nie zamykaj circuit breakera ręcznie bez potwierdzenia poprawy dependency.
4. Sprawdź wiek `PAYMENT_UNKNOWN`; przypadki `MANUAL_REVIEW` porównaj z ledgerem providera.
5. Po stabilizacji dependency sprawdź, czy half-open wraca do closed.
