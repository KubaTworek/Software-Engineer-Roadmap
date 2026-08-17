# Etap 4 — Optymalizacja

Ta wersja rozszerza Etap 3 o warstwę ML/optimization i szkielet multi-region active-active.

## Zakres

- `ml-eta-service` — predykcja ETA na podstawie dystansu, miasta, ruchu, pogody i typu pojazdu.
- `ml-matching-service` — ranking kandydatów kierowców z uwzględnieniem ETA, ratingu, akceptacji i anulowań.
- `demand-prediction-service` — prognoza popytu per miasto / H3 cell.
- `driver-positioning-service` — rekomendacje relokacji kierowców.
- `dynamic-pricing-service` — dynamic pricing oparty o live supply/demand i prognozę popytu.
- `region-control-plane` — home-region routing, health regionów i tryb active-active dla wybranych komponentów.
- `core-api` — dodany endpoint proxy `/api/v1/optimization/*` i migracja audytu predykcji/region routing.

## Uwagi produkcyjne

Modele ML są tu świadomie deterministycznymi baseline'ami, nie prawdziwymi modelami uczonymi offline. To właściwy etap architektoniczny: kontrakty, feature ingestion, audyt predykcji, integracje i fallbacki są ważniejsze niż dokładność modelu demonstracyjnego.

Docelowo baseline'y należy zastąpić modelem serwowanym przez np. MLflow/Seldon/KServe, a feature store przez Feast/Tecton albo własny feature pipeline.

## Multi-region

Wersja developerska zawiera `region-control-plane` i opcjonalny `docker-compose.multi-region.yml`. Nie jest to pełna replikacja baz danych ani MirrorMaker 2. Wskazuje jednak miejsca, w których trzeba dodać:

- globalny routing DNS/GSLB,
- multi-region Kafka replication,
- lokalną silną spójność dla aktywnych przejazdów,
- globalną eventual consistency dla profili, scoringów i analityki,
- conflict resolution per aggregate.

## Uruchomienie

```bash
docker compose up --build
```

Opcjonalny szkic multi-region:

```bash
docker compose -f docker-compose.yml -f docker-compose.multi-region.yml up --build
```
