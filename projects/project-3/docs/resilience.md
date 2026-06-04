# Resilience

Planowane mechanizmy:

- timeouts na klientach HTTP,
- circuit breaker dla Payment Service,
- retry z exponential backoff,
- idempotency key dla tworzenia zamówień,
- graceful degradation do `PAYMENT_PENDING`,
- cache Redis dla katalogu,
- rate limiting w API Gateway.
