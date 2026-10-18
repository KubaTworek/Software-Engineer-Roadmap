# Stage 3 — Scaling Notes

## Najważniejsza zmiana

Stage 3 nie powinien być tylko dopisaniem kolejnych klas do monolitu. Lokalizacja, real-time fanout, pricing, fraud i warehouse mają inne profile obciążenia niż core API, dlatego zostały wydzielone do osobnych usług.

## Location Service

Lokalizacja jest write-heavy. Redis przechowuje tylko aktualny live state, a Kafka przenosi strumień zdarzeń dalej. Monotoniczny `sequence` odrzuca pakiety spóźnione; `receivedAt` wyznacza świeżość niezależnie od clock skew telefonu.

Klucze Redis:

```text
loc:driver:{driverId}
loc:city:{cityId}:h3:{h3Cell}
loc:city:{cityId}:available
```

## Sharding per city

City-based sharding działa dobrze, bo przejazdy są lokalne. `cityId` jest częścią kontraktu ride, pricingu i location, nie stałą adaptera. Wersja dev nadal używa jednej bazy; przejście do shardów wymaga routingu właściciela zapisu, migracji danych i fencing tokenów, nie tylko `AbstractRoutingDataSource`.

## Real-time Gateway

Gateway jest stateless z punktu widzenia biznesu. Każdy węzeł używa osobnej grupy Kafka, bo musi zobaczyć eventy dla własnych socketów. JWT na STOMP `CONNECT` i prywatne kolejki chronią dane uczestników. REST pozostaje źródłem prawdy po reconnect. W production warto zastąpić prosty broker STOMP brokerem zewnętrznym albo własnym fanoutem przez Redis/NATS.

## Pricing

Pricing jest oddzielony, bo będzie zmieniany często i może później używać ML/modeli popytu. Stage 3 zawiera prosty surge multiplier.

## Fraud

Fraud Service zbiera sygnały z Kafka i udostępnia synchroniczne API scoringowe. To pozwala blokować ryzykowne operacje w core-api bez duplikowania logiki fraudowej.

## Data Warehouse

Wersja developerska zapisuje eventy do PostgreSQL i deduplikuje je po `eventId`. Offset powinien być zatwierdzony dopiero po zapisie. W production lepsze będą BigQuery, Snowflake, Redshift, ClickHouse albo data lake na S3/GCS, lecz nadal potrzebują replayu, checkpointów i kontroli ewolucji schematu.

## Profile uruchomieniowe

- domyślny profil `core-api`: lokalny pricing i Redis GEO, użyteczny do nauki przepływu;
- `distributed`: adaptery HTTP do Location i Pricing Service;
- `infrastructure-tests`: testy prawdziwego PostgreSQL i Redisa przez Testcontainers.
