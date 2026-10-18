# Mapa gwarancji Stage 3

| Problem | Niezmiennik lub cel | Implementacja | Dowód | Sygnał produkcyjny | Granica |
| --- | --- | --- | --- | --- | --- |
| Overselling | `0 <= available <= total` | idempotentny bootstrap oraz atomowy `UPDATE` w Reservation DB | `PostgreSqlInventoryConcurrencyTest` | odrzucone rezerwacje, inventory gauge | zmiana pojemności wymaga osobnego kontraktu, nie resetu projekcji |
| Redelivery | jeden lokalny efekt na `messageId` | outbox producenta + atomowy inbox konsumenta | test DLQ na RabbitMQ i test terminalnego poison outbox | backlog, `app_outbox_dead`, DLQ | transport pozostaje at-least-once |
| Niepewna płatność | timeout nie oznacza porażki | `PAYMENT_UNKNOWN`, lookup, retry budget, refund i trwały ledger PostgreSQL | test stanów, idempotentnej kompensacji i restart w smoke teście | wiek zamówień unknown/compensation | mock upraszcza settlement, chargeback i webhooki providera |
| Stary właściciel | zapis z niższym tokenem jest odrzucony | PostgreSQL lease i token w inventory | scenariusz dwóch właścicieli w `PostgreSqlInventoryConcurrencyTest` | odrzucone stale writes | correctness pochodzi z fenced resource, nie z samego lease |
| Budżet czasu | próba mieści się w deadline | filtr i propagacja `X-Request-Deadline-Epoch-Millis` | `DeadlineAndAdmissionTest`, `DeadlineFilterTest` | deadline exceeded, czas prób | async messaging wymaga osobnego expiry/deadline |
| Przeciążenie | liczba prac w toku jest ograniczona | `AdmissionController` w klientach payment/reservation | test load shedding | 503, rejected calls, saturation | limity muszą wynikać z capacity downstreamu |
| Recovery | odtworzenie spełnia RPO/RTO | plan DR i wykonywalny lokalny restore | `local-restore-drill.ps1` oraz operational drill | backup age, restore duration | dev pozostaje single-AZ ze względu na koszt |

## Kolejność czytania

1. Zacznij od inventory i `PostgreSqlInventoryConcurrencyTest`.
2. Przejdź przez stany Order oraz failure windows płatność → rezerwacja → outbox.
3. Uruchom test fencing oraz deadline/load shedding.
4. Uruchom scenariusze k6 i powiąż p99 z trace'em oraz saturation.
5. Oceń plan DR i warianty Terraform dev/production.
