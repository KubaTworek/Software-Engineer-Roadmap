# Etap 5 — SQL i performance

Cel etapu: nie zgadywać, tylko mierzyć. Ten etap dodaje endpointy pod realne zapytania OLTP, generator dużego datasetu i skrypty `EXPLAIN (ANALYZE, BUFFERS)`.

## Dlaczego generator nie odpala się w zwykłych testach

Pełny dataset ma 100 000 eventów i 1 000 000 rezerwacji. Taki seed nie może być częścią `mvn test`, bo testy byłyby wolne, niestabilne i zależne od sprzętu. Zwykłe testy sprawdzają poprawność endpointów na małym zbiorze. Pełny pomiar robisz świadomie na lokalnym PostgreSQL.

## Uruchomienie dużego datasetu

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=performance-seed
```

Domyślne wartości:

```yaml
performance.seed.organizations: 100
performance.seed.customers: 10000
performance.seed.events: 100000
performance.seed.reservations: 1000000
```

Możesz je zmniejszyć lokalnie, np. na słabszej maszynie:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.profiles=performance-seed \
  -Dspring-boot.run.arguments="--performance.seed.events=10000 --performance.seed.reservations=100000"
```

## Endpointy

### Search eventów

```http
GET /api/events?city=Warsaw&from=2026-06-01T00:00:00Z&category=music
```

Indeks:

```sql
CREATE INDEX idx_event_city_category_start_time
ON events(city, category, starts_at);
```

Ten indeks pasuje do filtrów równościowych po `city` i `category` oraz zakresu/sortowania po `starts_at`.

### Rezerwacje organizacji — offset pagination

```http
GET /api/organizations/{organizationId}/reservations?status=CONFIRMED&page=0&size=50
```

Indeks:

```sql
CREATE INDEX idx_reservation_org_status_created_at
ON reservations(organization_id, status, created_at DESC);
```

To jest klasyczny read-heavy endpoint dla panelu organizacji.

### Rezerwacje klienta — offset pagination

```http
GET /api/customers/{customerId}/reservations?page=0&size=50
```

### Rezerwacje klienta — keyset pagination

```http
GET /api/customers/{customerId}/reservations/keyset?size=50
GET /api/customers/{customerId}/reservations/keyset?afterCreatedAt=2026-06-01T00:00:00Z&size=50
```

Indeks:

```sql
CREATE INDEX idx_reservation_customer_created_at_id
ON reservations(customer_id, created_at DESC, id DESC);
```

Offset pagination jest wygodna dla UI, ale przy dużym `OFFSET` baza musi ominąć wiele rekordów. Keyset pagination zwykle skaluje się lepiej, bo kontynuuje od kursora.

### Statystyki eventu

```http
GET /api/events/{eventId}/stats
```

Zapytanie grupuje rezerwacje po statusie. Przy dużym evencie warto obserwować plan wykonania i koszt agregacji.

## N+1

Dodane endpointy edukacyjne:

```http
GET /api/events/{eventId}/reservations/n-plus-one
GET /api/events/{eventId}/reservations/fetch-join
GET /api/events/{eventId}/reservations/entity-graph
```

Pierwszy wariant ładuje rezerwacje i dopiero podczas mapowania DTO dotyka `event` i `customer`. To może powodować N+1. Drugi wariant używa `join fetch`, trzeci `@EntityGraph`.

## EXPLAIN ANALYZE

Skrypty:

```text
docs/sql-performance/explain-before-indexes.sql
docs/sql-performance/explain-after-indexes.sql
```

Procedura:

1. Wygeneruj dataset.
2. Wybierz prawdziwe UUID organizacji i klienta:

```sql
SELECT id FROM organizations LIMIT 1;
SELECT id FROM customers LIMIT 1;
```

3. Podmień placeholdery w skryptach.
4. Uruchom wersję bez indeksów.
5. Uruchom wersję z indeksami.
6. Porównaj:
   - `Seq Scan` vs `Index Scan` / `Bitmap Index Scan`,
   - `Sort`,
   - `Nested Loop` / `Hash Join`,
   - `actual time`,
   - `rows`,
   - `Buffers`.

## Czego oczekiwać

Dla event search po dodaniu indeksu powinieneś zobaczyć mniej skanowanych wierszy i plan oparty o indeks. Dla dużego offsetu nadal możesz zobaczyć koszt rosnący wraz z numerem strony — indeks pomaga, ale nie usuwa fundamentalnego kosztu `OFFSET`. Keyset powinien być stabilniejszy przy dalszych stronach.


## Hibernate batch fetching note

Dla redukcji części problemów N+1 projekt używa globalnej właściwości:

```yaml
spring.jpa.properties.hibernate.default_batch_fetch_size: 50
```

Nie używamy `@BatchSize` bezpośrednio na polach `@ManyToOne`, ponieważ w Hibernate 6 / Spring Boot 3 może to powodować błąd startu kontekstu przy przetwarzaniu adnotacji. Dla głównych zapytań Stage 5 nadal preferowane są jawne strategie: DTO projection, `fetch join` albo `@EntityGraph`.
