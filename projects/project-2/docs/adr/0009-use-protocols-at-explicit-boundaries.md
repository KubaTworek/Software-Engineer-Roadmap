# ADR 0009: Use REST, GraphQL, gRPC and WebSocket at explicit boundaries

## Status

Accepted as a selection policy; the project does not expose every use case through
every protocol.

## Decision

REST is the default public command/resource API. GraphQL is reserved for flexible
read models and requires complexity limits, batched loading and field authorization.
gRPC is an internal RPC option and must propagate deadline/cancellation and preserve
Protobuf field numbers. WebSocket only signals changes and maintains bounded queues;
the durable resource is still read through an API.

Protocol DTOs map to application commands or query models. Aggregates never depend
on HTTP, GraphQL, Protobuf or WebSocket classes.

## Consequences

This avoids three public models becoming one accidental shared model. It also means
GraphQL ma resolver Spring GraphQL, gRPC korzysta z kodu wygenerowanego z Protobuf i
opcjonalnego osobnego serwera, a WebSocket ma adapter Spring z bounded session model.
Nie oznacza to, że jeden use case jest wystawiany wszystkimi protokołami.
