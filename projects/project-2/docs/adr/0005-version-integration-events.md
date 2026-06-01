# ADR 0005: Version integration events explicitly

## Context

Integration events are contracts between modules and, eventually, between
separate deployables. Once an event is published, consumers may continue to rely
on its shape and semantics for a long time.

Changing an event in place is risky because older consumers may fail at runtime
or, worse, process data incorrectly.

## Decision

Every integration event envelope contains an explicit `eventVersion`.

`OrderPlaced` is the first versioned event with two supported contracts:

- `OrderPlaced` V1,
- `OrderPlaced` V2.

Consumers do not deserialize wire payloads directly into business handlers.
Instead, the integration layer maps versioned contracts to a normalized internal
domain event.

Unsupported versions fail explicitly. In the Kafka consumer flow they are retried
according to policy and then sent to DLQ.

## Consequences

Positive:

- consumers can support more than one event version,
- adding optional fields does not break existing flow,
- breaking changes are visible and testable,
- DLQ contains clear information about unsupported versions.

Negative:

- additional mapping code is required,
- contract documentation must be maintained,
- tests must cover compatibility, not only happy-path deserialization.

## Alternatives considered

### Single Java event class only

Rejected. It hides the difference between wire contracts and internal domain
models. It also makes historical compatibility difficult to reason about.

### New topic per event version

Not selected for this stage. It can be useful for large breaking migrations, but
for this modular monolith stage explicit envelope versioning is enough.
