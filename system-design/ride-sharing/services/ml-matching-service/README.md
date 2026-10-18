# ML Matching Service

## Sens laboratorium

Model rankuje kandydatów, ale nie rezerwuje kierowcy. Niezmiennik: **predykcja nigdy nie zastępuje atomowego claimu w systemie transakcyjnym**.

Krytyczny przepływ: ograniczona geograficznie lista → cechy ETA, acceptance i fairness → stabilny score → ranking → core próbuje atomowo zarezerwować kolejnych kandydatów.

Rekomendowany test: `MatchingModelServiceTest`.

## Granica produkcyjna

Baseline nie zawiera uczenia, eksploracji, fairness constraints ani wykrywania feedback loop. Produkcyjnie trzeba wersjonować cechy i model, mierzyć acceptance/cancellation oraz zachować heurystyczny fallback.
