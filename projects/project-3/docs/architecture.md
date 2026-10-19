# Architecture

## Kontekst

System obsługuje katalog wydarzeń, rezerwacje, zamówienia, płatność mockowaną i powiadomienia.

## Granice serwisów

- `catalog-service` odpowiada za dane read-heavy; jego availability może być opóźnioną projekcją.
- `reservation-service` posiada autorytatywne inventory i chroni je atomowym warunkowym SQL.
- `order-service` odpowiada za workflow zamówienia i idempotencję.
- `payment-mock-service` jest kontrolowaną awaryjną zależnością downstream.
- `notification-service` konsumuje eventy asynchronicznie.

## Zaimplementowane granice poprawności

- Inventory jest atomowo zmniejszane w bazie Reservation Service.
- Nowy event inicjalizuje lokalne inventory przez idempotentny bootstrap; ponowiony
  snapshot nie nadpisuje miejsc zużytych już przez rezerwacje, a cache kontraktu jest wersjonowany.
- Order Service zapisuje event do outboxa w transakcji z zamówieniem.
- Notification Service atomowo rejestruje inbox marker i kieruje poison messages do DLQ.
- Workflow zamówienia rozróżnia niepewną płatność, oczekiwanie na rezerwację,
  kompensację przez idempotentny refund oraz przypadek wymagający ręcznej decyzji.
- Deadline jest propagowany przez HTTP, a osobne bulkheady ograniczają zależności payment i reservation.
- Outbox po wyczerpaniu budżetu prób zachowuje poison record i eksponuje osobną metrykę.

## Świadome granice edukacyjne

- Gateway robi prosty proxying, nie pełny production-grade routing.
- Payment Mock utrzymuje trwały, idempotentny ledger płatności i refundów w osobnej
  bazie PostgreSQL. Nadal upraszcza protokół zewnętrznego providera, settlement i chargeback.
- Worker expiry używa PostgreSQL jako koordynatora lease i przekazuje fencing token do inventory.
- Dev cloud pozostaje kosztowo uproszczony; profil produkcyjny powinien włączyć HA i DR.
