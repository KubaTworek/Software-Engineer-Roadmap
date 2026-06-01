# ADR 0006: Add observability to asynchronous flow

## Context

After introducing Kafka-style asynchronous consumers, failures are harder to diagnose. A single order can move through outbox, topics, consumers, retries and DLQ. Without correlation IDs, structured logs and metrics, debugging requires guessing.

## Decision

We add an explicit observability layer:

- correlation ID propagated in events,
- MDC-based structured logging,
- flow trace entries searchable by `correlationId` and `orderId`,
- counters and gauges for consumer processing,
- consumer lag metric,
- retry and DLQ counters,
- DLQ reason endpoint,
- health indicators for outbox and DLQ.

## Consequences

Benefits:

- a single order flow can be traced end-to-end,
- duplicate events are visible,
- retry storms are easier to detect,
- DLQ entries include actionable reasons,
- operators can distinguish healthy, degraded and broken states.

Costs:

- more infrastructure code,
- every integration component must consistently call observability hooks,
- in-memory metrics are not enough for production.

## Alternatives considered

### Only logs

Rejected. Logs alone do not give easy counters, lag or health state.

### Full OpenTelemetry immediately

Deferred. It is the correct production direction, but too heavy for this educational phase. This implementation keeps the same concepts while remaining easy to test.
