# SLO i error budget

| User journey | SLI | Cel w 30 dniach | Wyłączenia |
| --- | --- | --- | --- |
| przegląd katalogu | poprawne odpowiedzi inne niż jawne 4xx | 99.9% | kontrolowane 429 klienta |
| utworzenie rezerwacji | decyzja w 1 s i brak oversellingu | 99.95% poprawności, p99 < 1 s | brak inventory jest poprawną odmową |
| przyjęcie zamówienia | trwały stan PENDING/UNKNOWN/PAID | 99.9%, p99 < 3 s | błąd walidacji klienta |
| powiadomienie | event przetworzony w 60 s | 99.5% | wiadomości świadomie umieszczone w DLQ |

Error budget dla 99.9% wynosi około 43 minut niedostępności w 30 dniach. Alert powinien
reagować na tempo spalania budżetu, a nie na pojedynczy błąd. Szybkie spalanie uruchamia
incident, wolne spalanie zatrzymuje ryzykowny rollout. SLO nie może obejmować samych statusów
HTTP: brak oversellingu i brak podwójnej płatności są równie ważnymi SLI poprawności.
