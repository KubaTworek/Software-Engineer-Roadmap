# ML ETA Service

## Sens laboratorium

Usługa pokazuje granicę inferencji ETA: model może poprawiać estymację routingu, ale nie wolno pozwolić mu zwracać fizycznie niemożliwych wartości. Niezmiennik: **wynik jest dodatni, skończony i ograniczony guardrailami**.

Krytyczny przepływ: cechy trasy i kontekstu → deterministyczny baseline modelu → korekta → walidacja i clamp → odpowiedź z wersją modelu. Reprodukowalność wejścia i wersji jest ważniejsza niż pozorna „inteligencja” przykładu.

Rekomendowany test: `EtaModelServiceTest`.

## Granica produkcyjna

Nie ma tu treningu, feature store, canary modelu ani monitoringu driftu. Produkcyjny system porównuje model z routing baseline, mierzy błąd per segment/miasto i potrafi natychmiast wrócić do heurystyki.
