# ADR 0008: Enforce at-least-once correctness at database boundaries

## Status

Accepted.

## Context

Kafka, PostgreSQL and the application do not share one atomic commit. Multiple
application replicas may also publish the same outbox and update the same Order.
Plain `SELECT`, check-then-insert and UPSERT hide races rather than solve them.

## Decision

- the consumer's business effect, generated outbox events and processed marker use
  one local database transaction;
- Kafka offset is committed per partition only after that transaction succeeds;
- `(event_id, consumer_name)` remains the final idempotency constraint;
- outbox rows are atomically claimed with `FOR UPDATE SKIP LOCKED` and stale claims
  can be recovered;
- Order updates use an optimistic `version` predicate;
- transient concurrency failures have a bounded retry budget, while invalid
  contracts go directly to DLQ.

## Consequences

Delivery remains at-least-once. A crash after broker publish and before
`markPublished` may duplicate a message, so every consumer and external effect must
still be idempotent. Hot aggregates may need serialization or partitioning when
optimistic conflicts become normal rather than exceptional.
