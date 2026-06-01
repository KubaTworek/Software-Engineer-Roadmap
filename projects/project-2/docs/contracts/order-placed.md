# Contract: OrderPlaced

## Event name

`OrderPlaced`

## Current version

`2`

## Topic

`marketplace.order-events.v1`

The topic name is intentionally not changed for every payload version. The event
version is part of the envelope and payload handling strategy.

## Version 1

Historical contract.

Required fields:

- `eventId`
- `orderId` or `aggregateId`
- `customerId`
- `totalAmount`
- `currency`
- `occurredAt`
- `correlationId`

Money is flat:

```json
{
  "totalAmount": "199.99",
  "currency": "PLN"
}
```

## Version 2

Current contract.

Required fields:

- `eventId`
- `aggregateId` or `orderId`
- `customerId`
- `total.amount`
- `total.currency`
- `occurredAt`
- `correlationId`

Money is structured:

```json
{
  "total": {
    "amount": "199.99",
    "currency": "PLN"
  }
}
```

Optional fields:

- `salesChannel`

## Compatibility policy

Consumers must ignore unknown fields.

A producer may add optional fields without creating a new version if consumers
can safely ignore them. Removing required fields or changing field types requires
a new major event version and explicit consumer support.
