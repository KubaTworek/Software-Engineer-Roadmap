# Stage 8 — NoSQL i cache

Ten etap dodaje Redis i MongoDB jako świadome dodatki do monolitu, ale **PostgreSQL nadal pozostaje źródłem prawdy** dla rezerwacji, płatności, dostępności i danych użytkowników.

Domyślne testy używają implementacji in-memory, żeby `mvn test` nie wymagał działającego Redisa ani MongoDB. Realne implementacje Redis/Mongo są dostępne po uruchomieniu profilu `nosql-real`.

## Redis jako key-value store

Redis jest używany do przypadków, w których model key-value i TTL są naturalne:

- cache detali eventu,
- cache snapshotu dostępności,
- rate limiting,
- tymczasowe holdy rezerwacji z TTL.

Endpointy edukacyjne:

```http
GET /api/nosql/cache/events/{eventId}
GET /api/nosql/cache/events/{eventId}/availability
DELETE /api/nosql/cache/events/{eventId}
POST /api/nosql/cache/reservation-holds?eventId={eventId}&customerEmail=a@example.com
GET /api/nosql/cache/reservation-holds/{holdId}
POST /api/nosql/cache/rate-limit/{clientKey}
```

### Ważny trade-off

Snapshot dostępności w cache może być chwilowo nieaktualny. To jest przykład eventual consistency. W tym projekcie główny flow rezerwacji nadal wykonuje atomowy update w PostgreSQL. Cache nie decyduje o sprzedaży miejsca.

## MongoDB jako dokumentowy read model

MongoDB reprezentuje denormalizowany read model:

```text
EventSearchDocument
  eventId
  name
  city
  category
  startsAt
  organizationId
  organizationName
  totalCapacity
  availableCapacity
  reservationsByStatus
  rebuiltAt
```

Endpointy edukacyjne:

```http
POST /api/nosql/read-model/events/{eventId}/rebuild
GET /api/nosql/read-model/events/{eventId}
GET /api/nosql/read-model/events?city=Warsaw&category=music&limit=20
DELETE /api/nosql/read-model/events
```

Read model jest budowany z PostgreSQL. Po zmianie rezerwacji dokument może być nieaktualny do czasu kolejnego rebuilda. To pokazuje problem read-your-writes.

## Uruchamianie z prawdziwym Redisem i MongoDB

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=nosql-real
```

Profil `nosql-real` przełącza implementacje:

- `InMemoryEventDetailsCache` -> `RedisEventDetailsCache`,
- `InMemoryAvailabilitySnapshotCache` -> `RedisAvailabilitySnapshotCache`,
- `InMemoryReservationHoldStore` -> `RedisReservationHoldStore`,
- `InMemoryRateLimiterStore` -> `RedisRateLimiterStore`,
- `InMemoryEventSearchReadModelStore` -> `MongoEventSearchReadModelStore`.

## Distributed lock

Distributed lock nie został użyty jako domyślna strategia rezerwacji. To celowe. W tym projekcie overselling jest rozwiązany atomowym update'em w PostgreSQL. Redis lock byłby dodatkową złożonością i mógłby dawać fałszywe poczucie bezpieczeństwa, jeżeli nie obsłużysz TTL, zegarów, retry, awarii klienta i split-brain.

## Testy

```bash
mvn test -Dtest=NoSqlCacheStage8IntegrationTest
mvn test -Dtest=EventReadModelStage8IntegrationTest
mvn test -Dtest=ApiNoSqlStage8IntegrationTest
```

Testy sprawdzają:

- cache miss -> SQL -> cache hit,
- invalidację availability cache po rezerwacji,
- TTL-style reservation hold,
- rate limiting,
- budowanie dokumentowego read modelu,
- celową nieaktualność read modelu do czasu rebuilda,
- endpointy HTTP dla cache i read modelu.
