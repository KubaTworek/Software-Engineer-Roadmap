# Stage 2 — Stabilization Notes

## Co zostało dodane

1. `idempotency` — ochrona przed wielokrotnym wykonaniem requestów.
2. `outbox` — niezawodna publikacja eventów do Kafki.
3. Kafka — `ride.events`, `payment.events`, `support.events`.
4. `RideStateMachine` — twarde reguły przejść statusów przejazdu.
5. `ride_status_history` — audyt zmian statusu.
6. Resilience4j — retry/circuit breaker dla map i płatności.
7. Support tools — tickety użytkowników i panel operatora.
8. Admin API — przeglądanie użytkowników, kierowców, przejazdów i historii statusów.
9. Observability — `/actuator/metrics`, `/actuator/prometheus`, Prometheus config.

## Następne kroki

- dodać DLQ dla eventów outbox,
- dodać integracyjne testy z Testcontainers,
- wdrożyć pełny admin frontend,
- dodać reconciliation job dla płatności,
- dodać szczegółowy audit log dla admin/support,
- wydzielić Location/Matching jako osobne serwisy w Etapie 3.
