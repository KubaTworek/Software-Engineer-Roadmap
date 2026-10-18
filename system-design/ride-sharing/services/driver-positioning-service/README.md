# Driver Positioning Service

## Sens laboratorium

Usługa tłumaczy prognozę popytu na rekomendacje, gdzie kierowcy powinni się przemieścić. Niezmiennik: **rekomendacja jest poradą, nie rezerwacją podaży ani obietnicą przychodu**.

Krytyczny przepływ: typowany `DemandForecast` → wybór sąsiednich komórek o deficycie → ranking rekomendacji → odpowiedź z horyzontem i pewnością. Klient HTTP ma jawne connect/read timeouty, aby niedostępna predykcja nie blokowała systemu.

Rekomendowany test: `DriverPositioningServiceTest`, który dowodzi stabilnego rankingu i deterministycznego fallbacku, gdy upstream jest niedostępny.

## Granica produkcyjna

Brakuje modelu czasu dojazdu, incentives, ograniczeń geograficznych i eksperymentów wpływu na zarobki kierowców. Rekomendacje wymagają kontroli fairness i nie mogą sterować krytycznym matchingiem.
