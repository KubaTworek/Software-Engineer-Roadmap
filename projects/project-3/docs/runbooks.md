# Runbooks

## DB down

Objawy: wzrost error rate, timeouty, saturacja connection poola.

Pierwsze kroki:

1. sprawdź `/actuator/health`,
2. sprawdź metryki HikariCP,
3. sprawdź logi błędów po `correlationId`,
4. ogranicz ruch write-heavy,
5. nie zwiększaj retry bez limitu.

## Redis down

Objawy: wzrost latency katalogu, spadek cache hit ratio, możliwe problemy z rate limitingiem.

## Payment failure

Objawy: circuit breaker open, wzrost `PAYMENT_PENDING`, większe p95/p99 w Order Service.

## Broker lag

Objawy: kolejka `notifications.order-paid` rośnie szybciej niż consumer przetwarza wiadomości.
