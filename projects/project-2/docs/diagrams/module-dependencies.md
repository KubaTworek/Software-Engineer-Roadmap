# Module Dependencies

```mermaid
flowchart LR
    Catalog --> Ordering
    Ordering --> Payment
    Ordering --> Inventory
    Ordering --> Fulfillment
    Ordering --> Notification
    Payment --> Ordering
    Inventory --> Ordering
```
