# ADR 0003: Use Outbox Pattern

## Status

Accepted

## Context

In the previous stage, modules communicated through an in-memory application event bus. This decoupled modules, but event publication was still coupled to the current process execution. If the application crashed after changing an aggregate but before publishing an event, the event could be lost.

This is especially dangerous for order processing. For example, if `OrderPlaced` is lost, payment and inventory modules will never reserve payment or stock.

## Decision

We will use the Outbox Pattern.

Application services and handlers will save domain changes and outbox events in the same transaction. Event handlers will not be called directly from the use case. Instead, `OutboxWorker` will later read `NEW` events from the outbox and publish them through the internal `ApplicationEventBus`.

Outbox events have explicit status:

- `NEW`
- `PUBLISHED`
- `FAILED`

Failed publications can be retried automatically or manually through admin endpoints.

## Consequences

Benefits:

- Events are not lost when the application crashes after aggregate persistence.
- Event publication can be retried.
- Operational visibility improves because outbox state is queryable.
- This prepares the system for a broker-based architecture later.

Costs:

- Event publication becomes eventually consistent.
- We need a worker process.
- We need cleanup/retention policy later.
- Handlers must be idempotent in future stages, because retries can happen.

## Alternatives considered

### Publish directly from use case

Rejected. It is simple, but event loss is possible when the application crashes between aggregate save and event publication.

### Use Kafka immediately

Rejected for this stage. The goal is to first make publication reliable inside the modular monolith. Kafka will be introduced later.

### Synchronous transaction-spanning module calls

Rejected. This would couple modules too strongly and hide consistency boundaries.
