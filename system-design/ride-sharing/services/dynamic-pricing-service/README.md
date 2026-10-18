# Dynamic Pricing Service

## Sens laboratorium

Usługa przekształca prognozę popytu w kontrolowany mnożnik ceny. Niezmiennik: **awaria, skrajne wejście ani błąd predykcji nie mogą ominąć minimalnego i maksymalnego guardraila**.

Krytyczny przepływ: typowany `DemandForecast` → relacja demand/supply → wygładzony multiplier → clamp → wynik z uzasadnieniem. Timeout upstreamu jest krótki, bo dynamic pricing nie powinien zużywać całego budżetu requestu.

Rekomendowany test: `DynamicPricingGuardrailTest`.

## Granica produkcyjna

Brakuje signed quote, polityk regulacyjnych, anty-oscylacji i eksperymentów. Produkcyjnie mnożnik powinien mieć wersję, TTL, limity zmian w czasie oraz bezpieczny fallback do taryfy bazowej.
