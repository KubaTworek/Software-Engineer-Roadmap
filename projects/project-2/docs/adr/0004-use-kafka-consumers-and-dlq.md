# ADR 0004: Use Kafka consumers, processed_events and DLQ

## Context

The internal event bus and local outbox relay are useful teaching boundaries, but they do not model real asynchronous processing, consumer groups, offsets, duplicate delivery or poison messages.

## Decision

Outbox events are published to Kafka topics. The application runtime uses
`@KafkaListener` containers with one consumer group per bounded-context reaction
and manual acknowledgement only after successful processing. The deterministic
manual-poll adapter remains a laboratory for inspecting offsets. Consumers store
`(event_id, consumer_name)` in `processed_events` to make duplicate delivery safe.
Events that cannot be processed after retries are moved to DLQ.

## Consequences

Positive:

- duplicate events do not corrupt data,
- a crash after business side effect but before offset commit can be handled safely,
- invalid events do not block the whole partition forever,
- replay from DLQ is explicit.
- a scoped dispatcher prevents one group from executing handlers owned by another module.

Negative:

- consumers are more complex,
- idempotency must be designed per consumer,
- retry and DLQ require operational ownership.

## Alternatives considered

- Keep only in-memory event bus: simpler, but hides production failure modes.
- Commit offsets before processing: faster, but can lose events.
- No DLQ: simpler, but poison events can block processing indefinitely.
