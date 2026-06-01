# 0002 — Use internal application event bus

## Context

The project is a modular monolith. Modules should not call other modules' implementation details directly. The second phase requires event-based communication inside the monolith, but without Kafka/RabbitMQ yet.

## Decision

Use a synchronous in-process `ApplicationEventBus` based on `DomainEvent` and `DomainEventHandler<T>`.

`ordering` publishes `OrderPlaced`. `payment` and `inventory` react to it. They publish `PaymentReserved`, `PaymentRejected`, `StockReserved` or `StockReservationFailed`. `ordering` reacts to these events and changes order state.

## Consequences

Positive:

- Module coupling is lower than direct service calls.
- Event flow can be tested without Spring Context, Kafka or database.
- The design prepares the project for outbox and broker-based integration.

Negative:

- Events are not durable yet.
- Handler execution is synchronous.
- Failures during event publication are not isolated yet.
- There is no retry, DLQ or replay yet.

## Alternatives considered

- Direct calls from `ordering` to `payment` and `inventory`: rejected because it hides module boundaries.
- Kafka from the start: rejected because it would add operational complexity before the domain boundaries are proven.
- Spring Application Events: not selected for core tests because explicit interfaces are easier to reason about and test without Spring.
