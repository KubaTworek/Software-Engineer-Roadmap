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

## Trwała intencja przed płatnością

OrderService.create ma dwie jawne granice TransactionTemplate/REQUIRES_NEW:
prepare zapisuje PENDING i nextAttemptAt oraz kończy COMMIT; dopiero reconcile
wykonuje płatność, confirm oraz lokalny zapis PAID + outbox. Sam save/flush nie
byłby trwałą intencją. Błąd commitu prepare uniemożliwia charge. Błąd commitu
wykonania pozostawia trwały PENDING, który obsłuży retry lub worker.

Każda próba używa tego samego orderId i klucza providera. Confirm jest idempotentny.
Worker obejmuje także PENDING/PAYMENT_IN_PROGRESS i serializuje repliki blokadą
wiersza. Niepowodzenie jednej sagi nie przerywa przeglądu pozostałych.
Outbox i PAID mają jeden lokalny commit; błąd outboxa nie jest połykany jako
przejściowa awaria HTTP. Test OrderCommitRecoveryInfrastructureTest wymusza
odroczony constraint trigger zarówno przy INSERT intencji, jak i końcowym UPDATE.

Blokada zasobu i UNIQUE(reservation_id) chronią także zmianę klucza przez klienta.
Istniejąca rezerwacja wymaga oryginalnego klucza; nowy klucz jest odrzucany,
aby nie przyjmować niezapisanego aliasu, który później pasowałby do innego requestu.
Przed walidacją bieżącego statusu rezerwacji odczytujemy istniejącą intencję:
CONFIRMED po utraconym commicie nie może uniemożliwić recovery. Migracja V5 nie
usuwa starych duplikatów; ich wykrycie wymaga uzgodnienia efektów finansowych.

Koszt: transakcja wykonania trzyma row lock i połączenie podczas ograniczonego
I/O. Duża skala wymaga krótkiego claimu z lease/fencing i oddzielnego potwierdzenia
wyniku. Nie skracaj transakcji przez usunięcie serializacji bez równoważnego
protokołu. Zewnętrzny provider nadal musi gwarantować idempotencję i lookup.

Poprawka zapobiega powstawaniu nowych sierot finansowych. Nie odtwarza automatycznie
zamówień utraconych przed jej wdrożeniem. Te rekordy wymagają uzgodnienia trwałego
ledgera providera, rezerwacji i logu operacji; nie wolno dopasowywać ich wyłącznie
po userId/kwocie ani ponownie obciążać klienta, aby sprawdzić wynik.
