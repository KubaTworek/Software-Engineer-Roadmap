# Stage 3 — Scaling Notes

## Najważniejsza zmiana

Stage 3 nie powinien być tylko dopisaniem kolejnych klas do monolitu. Lokalizacja, real-time fanout, pricing, fraud i warehouse mają inne profile obciążenia niż core API, dlatego zostały wydzielone do osobnych usług.

## Location Service

Lokalizacja jest write-heavy. Redis przechowuje tylko aktualny live state, a Kafka przenosi strumień zdarzeń dalej.

Klucze Redis:

```text
loc:driver:{driverId}
loc:city:{cityId}:h3:{h3Cell}
loc:city:{cityId}:available
```

## Sharding per city

City-based sharding działa dobrze, bo przejazdy są lokalne. Wersja dev ma resolver i migrację, ale nadal używa jednej bazy. Następny krok to `AbstractRoutingDataSource` lub rozdzielenie deploymentów per city/region.

## Real-time Gateway

Gateway jest stateless z punktu widzenia biznesu. Konsumuje Kafka i publikuje przez WebSocket. W production warto zastąpić prosty broker STOMP brokerem zewnętrznym albo własnym fanoutem przez Redis/NATS.

## Pricing

Pricing jest oddzielony, bo będzie zmieniany często i może później używać ML/modeli popytu. Stage 3 zawiera prosty surge multiplier.

## Fraud

Fraud Service zbiera sygnały z Kafka i udostępnia synchroniczne API scoringowe. To pozwala blokować ryzykowne operacje w core-api bez duplikowania logiki fraudowej.

## Data Warehouse

Wersja developerska zapisuje eventy do PostgreSQL. W production lepsze będą BigQuery, Snowflake, Redshift, ClickHouse albo data lake na S3/GCS.
