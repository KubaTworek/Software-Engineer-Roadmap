# ADR 0010: Persist the order fulfillment process

## Context

Payment and stock reservations finish independently. Keeping their progress only
in JVM memory loses facts on restart, waits forever when one event never arrives,
and can silently overwrite a concurrent transition.

## Decision

Persist one saga snapshot per order in PostgreSQL. The snapshot stores status,
completed steps, deadline and optimistic version. Every consumer reaction updates
the snapshot inside the same local transaction as its processed-event marker.
Facts may arrive before `OrderPlaced`, because Kafka provides no ordering across
topics. Expired pending processes enter compensation and record the required
release commands.

## Consequences

- restart no longer loses process progress;
- stale concurrent snapshots fail instead of dropping a payment or stock fact;
- a deadline makes missing messages observable and recoverable;
- consumers must retry optimistic conflicts with a bounded budget;
- release commands są zapisywane do outboxa, a Payment/Inventory wykonują lokalny,
  idempotentny efekt; zewnętrzna bramka nadal potrzebuje własnej idempotencji i reconciliation.

## Alternatives considered

- Keep the saga in memory: useful as a state-machine unit test, unsafe at runtime.
- Distributed transaction: couples independent resources and is unavailable for
  typical payment gateways and Kafka.
- Infer progress only from Order: hides compensations and timeout ownership.
