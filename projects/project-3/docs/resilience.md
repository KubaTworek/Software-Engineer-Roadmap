# Resilience

Zaimplementowane mechanizmy:

- timeouts na klientach HTTP,
- circuit breaker dla Payment Service,
- retry z exponential backoff,
- idempotency key dla tworzenia zamówień,
- `PAYMENT_UNKNOWN`, lookup providera, ograniczony retry, refund i `MANUAL_REVIEW`,
- deadline propagowany między serwisami oraz bulkhead per zależność,
- cache Redis dla katalogu,
- rate limiting w API Gateway.
