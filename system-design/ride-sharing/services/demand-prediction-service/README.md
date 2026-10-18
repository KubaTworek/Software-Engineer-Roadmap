# Demand Prediction Service

## Sens laboratorium

Usługa agreguje popyt w oknach czasowych zamiast utrzymywać wieczny licznik. Niezmiennik: **prognoza nie może zawierać danych starszych niż skonfigurowany horyzont**.

Krytyczny przepływ: `RideRequested` → bucket 15-minutowy per miasto/komórka → wygaszenie starych bucketów → snapshot `DemandForecast` z czasem i zakresem.

Rekomendowany test: `DemandPredictionWindowTest`, używający kontrolowanego `Clock`.

## Granica produkcyjna

Stan jest lokalny dla procesu. Produkcyjnie potrzebny jest partycjonowany stream processor lub współdzielony store, event-time watermarks, obsługa zdarzeń spóźnionych oraz rozróżnienie prognozy od obserwacji.
