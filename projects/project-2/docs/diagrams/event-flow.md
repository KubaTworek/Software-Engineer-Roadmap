# Event Flow

```mermaid
sequenceDiagram
    participant Client
    participant Ordering
    participant Payment
    participant Inventory
    participant Fulfillment
    participant Notification
    participant Process as Durable process manager
    participant Outbox
    participant Kafka

    Client->>Ordering: Place order
    Ordering->>Outbox: Order + OrderPlaced (local transaction)
    Outbox-->>Kafka: claimed publish
    Kafka-->>Payment: OrderPlaced (at-least-once)
    Kafka-->>Inventory: OrderPlaced (at-least-once)
    Kafka-->>Process: OrderPlaced / create deadline
    Payment-->>Kafka: PaymentReserved
    Inventory-->>Kafka: StockReserved
    Kafka-->>Ordering: result events
    Kafka-->>Process: result events / persist saga snapshot
    Ordering->>Outbox: OrderConfirmed
    Outbox-->>Kafka: OrderConfirmed
    Kafka-->>Fulfillment: OrderConfirmed (own group)
    Kafka-->>Notification: OrderConfirmed (own group)
```

Payment, Inventory i process manager mają różne grupy dla `OrderPlaced`.
Ordering i process manager mają różne grupy dla eventów wynikowych. Brak globalnej
kolejności między topicami jest częścią modelu: saga potrafi utworzyć snapshot po
pierwszym zaobserwowanym fakcie i później przyjąć spóźnione `OrderPlaced`.
