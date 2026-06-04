# Architecture

## Kontekst

System obsługuje katalog wydarzeń, rezerwacje, zamówienia, płatność mockowaną i powiadomienia.

## Granice serwisów

- `catalog-service` odpowiada za dane read-heavy.
- `reservation-service` odpowiada za rezerwacje i ochronę przed oversellingiem.
- `order-service` odpowiada za workflow zamówienia i idempotencję.
- `payment-mock-service` jest kontrolowaną awaryjną zależnością downstream.
- `notification-service` konsumuje eventy asynchronicznie.

## Świadome uproszczenia w bazowym szkielecie

- Inventory nie jest jeszcze atomowo zmniejszane.
- Gateway robi prosty proxying, nie pełny production-grade routing.
- Observability ma konfigurację startową, ale dashboardy trzeba rozwinąć.
- Outbox pattern nie jest jeszcze zaimplementowany.
