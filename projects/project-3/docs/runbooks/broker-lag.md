# Runbook — Broker lag / Notification Service lag

## Objaw

Zamówienia są opłacane, ale powiadomienia nie są przetwarzane albo są przetwarzane wolno.

## Metryki

```promql
increase(app_notifications_sent_total[10m])
```

W RabbitMQ Management sprawdź queue depth i unacked messages dla kolejki notifications.

## Logi

```logql
{service="notification-service"} |= "notification_sent"
```

## Trace

Trace powinien pokazać publikację eventu z Order Service. Jeśli event jest opublikowany, a brak konsumpcji, problem jest po stronie brokera albo Notification Service.

## Reakcja

- Sprawdź, czy Notification Service działa.
- Sprawdź błędy deserializacji eventu.
- Zwiększ liczbę consumerów tylko wtedy, gdy bottleneckiem jest processing, nie broker.
- Nie blokuj płatności z powodu awarii powiadomień.
