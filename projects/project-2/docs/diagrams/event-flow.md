# Event Flow

```mermaid
sequenceDiagram
    participant Client
    participant Ordering
    participant Payment
    participant Inventory
    participant Fulfillment
    participant Notification

    Client->>Ordering: Place order
    Ordering-->>Payment: OrderPlaced
    Ordering-->>Inventory: OrderPlaced
    Payment-->>Ordering: PaymentReserved
    Inventory-->>Ordering: StockReserved
    Ordering-->>Fulfillment: OrderConfirmed
    Ordering-->>Notification: OrderConfirmed
```
