# Phase 3 — Resilience

Faza 3 domyka pierwszy senior-level fragment projektu. Celem nie jest tylko dopisanie bibliotek, ale wymuszenie zachowania systemu pod awariami zależności, przeciążeniem i powtarzanymi requestami.

## Zakres zaimplementowany

| Mechanizm | Gdzie | Decyzja projektowa |
|---|---|---|
| Redis cache | `catalog-service` | Cache JSON z TTL, jitterem, single-flight i fallbackiem do DB, gdy Redis nie działa. |
| Rate limiting | `api-gateway` | Redisowy token bucket per IP albo per `X-API-Key`; działa między wieloma instancjami gatewaya. |
| Circuit breaker | `reservation-service`, `order-service` | Osobne circuit breakery dla `catalog`, `reservation`, `payment`. |
| Retry/backoff | `reservation-service`, `order-service` | Krótkie retry z exponential backoff; brak nieskończonych ponowień. |
| Graceful degradation | `api-gateway`, `catalog-service`, `order-service` | Gateway fail-open przy Redis down, katalog działa bez cache, order przechodzi do `PAYMENT_PENDING`. |
| Idempotency key | `order-service` | `Idempotency-Key` zwraca istniejące zamówienie zamiast tworzyć duplikat. |
| Timeouty HTTP | `common` | Wspólny timeout connect/response dla `WebClient` + lokalne `.timeout()` w klientach krytycznych. |

## Redis cache

`catalog-service` nie używa już prostego `@Cacheable`. Zamiast tego ma `RedisJsonCache`, który daje większą kontrolę:

- jawne klucze cache,
- TTL per typ danych,
- jitter TTL, żeby nie wygasały wszystkie klucze naraz,
- single-flight per instancja aplikacji,
- fallback do DB przy niedostępnym Redisie,
- metryki `app_cache_requests_total{cache=...,result=...}`.

Cache obejmuje:

```text
GET /events
GET /events/{id}
GET /events/{id}/availability
```

Konfiguracja:

```yaml
app:
  cache:
    events:
      ttl: 60s
    event-details:
      ttl: 120s
    availability:
      ttl: 10s
```

Uwaga: `availability` ma krótszy TTL, bo świeżość dostępności jest ważniejsza niż szczegóły wydarzenia.

## Rate limiting

`api-gateway` ma filtr `RateLimitFilter`. Klucz limitu zależy od nagłówka:

- jeśli jest `X-API-Key`, limit jest liczony per API key,
- jeśli nie ma `X-API-Key`, limit jest liczony per IP.

Limit jest liczony w Redisie przez Lua script jako token bucket. To jest ważne, bo limit działa poprawnie również wtedy, gdy gateway ma kilka instancji.

Konfiguracja:

```yaml
app:
  rate-limit:
    enabled: true
    anonymous:
      capacity: 60
      refill-tokens: 60
      refill-period: 60s
    api-key:
      capacity: 600
      refill-tokens: 600
      refill-period: 60s
```

Przy przekroczeniu limitu odpowiedź to:

```http
429 Too Many Requests
Retry-After: <seconds>
X-RateLimit-Remaining: <tokens>
```

Jeżeli Redis padnie, gateway działa w trybie fail-open i dodaje:

```http
X-RateLimit-Degraded: true
```

To jest świadoma degradacja: awaria Redisa nie powinna automatycznie wyłączyć całego API.

## Circuit breaker i retry

### `reservation-service -> catalog-service`

Tworzenie rezerwacji zależy od dostępności wydarzenia. Klient `CatalogClient` ma:

- retry `catalog`,
- circuit breaker `catalog`,
- timeout 2s,
- fallback, który bezpiecznie odrzuca rezerwację, gdy katalog jest niedostępny.

Decyzja: przy braku katalogu nie tworzymy rezerwacji „na ślepo”, bo grozi to oversellingiem.

### `order-service -> payment-mock-service`

Klient `PaymentClient` ma:

- retry `payment`,
- circuit breaker `payment`,
- timeout 2s,
- fallback do kontrolowanego wyjątku.

`OrderService` łapie problem z płatnością i ustawia status:

```text
PAYMENT_PENDING
```

To jest graceful degradation: zamówienie nie znika i nie blokuje requestu w nieskończoność. Można później dodać asynchroniczny retry płatności.

### `order-service -> reservation-service`

Klient `ReservationClient` ma:

- retry `reservation`,
- circuit breaker `reservation`,
- timeout 2s.

Tutaj degradacja jest ostrzejsza: jeśli nie da się odczytać rezerwacji, zamówienie nie powinno być tworzone.

## Idempotency key

`POST /orders` obsługuje nagłówek:

```http
Idempotency-Key: some-unique-client-key
```

Jeśli request zostanie powtórzony z tym samym kluczem, serwis zwróci istniejące zamówienie. To chroni przed duplikacją zamówień przy retry po stronie klienta albo gatewaya.

W bazie jest indeks:

```sql
CREATE UNIQUE INDEX ux_orders_idempotency_key ON orders(idempotency_key) WHERE idempotency_key IS NOT NULL;
```

## Timeouty HTTP

Wspólny moduł `common` zawiera `OutboundHttpClientConfig`, który ustawia timeouty dla wszystkich `WebClient.Builder`:

```yaml
app:
  http-client:
    connect-timeout-ms: 1000
    response-timeout-ms: 2500
```

Dodatkowo klienci krytyczni mają lokalne `.timeout(Duration.ofSeconds(2))`.

## Testy ręczne

### 1. Happy path

```bash
./scripts/smoke-test.sh
```

Oczekiwany wynik: rezerwacja i zamówienie powstaną, a status zamówienia będzie zwykle `PAID`, jeśli payment mock nie symuluje błędu.

### 2. Idempotency

```bash
reservation_id="<reservation-id>"
key="demo-idempotency-$reservation_id"

curl -s -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $key" \
  -d "{\"reservationId\":\"$reservation_id\",\"userId\":\"kubat\"}"

curl -s -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $key" \
  -d "{\"reservationId\":\"$reservation_id\",\"userId\":\"kubat\"}"
```

Oczekiwany wynik: oba requesty zwracają to samo `order.id`.

### 3. Rate limiting

Na czas testu możesz obniżyć limit:

```bash
RATE_LIMIT_ANON_CAPACITY=3 RATE_LIMIT_ANON_REFILL_TOKENS=3 docker compose --profile apps up --build api-gateway
```

Potem wykonaj kilka requestów:

```bash
for i in {1..10}; do
  curl -i http://localhost:8080/events | grep -E 'HTTP/|X-RateLimit|Retry-After'
done
```

Oczekiwany wynik: po kilku requestach pojawi się `429 Too Many Requests`.

### 4. Redis down

```bash
docker compose stop redis
curl -i http://localhost:8080/events
```

Oczekiwany wynik:

- gateway przepuści request w trybie fail-open,
- catalog-service ominie cache i użyje DB,
- w logach pojawi się informacja o degradacji cache/rate limitingu.

### 5. Payment failure

Uruchom `payment-mock-service` z wysokim failure rate:

```bash
PAYMENT_FAILURE_RATE=1.0 PAYMENT_MAX_DELAY_MS=100 docker compose --profile apps up --build payment-mock-service order-service
```

Potem wykonaj happy path. Oczekiwany wynik: zamówienie dostaje status `PAYMENT_PENDING`, a nie niekontrolowany błąd 500.

## Co jeszcze nie jest production-grade

To nadal nie jest finalna odporność produkcyjna. Brakuje m.in.:

- asynchronicznego retry płatności z kolejki,
- dead-letter queue dla eventów,
- rozproszonego single-flight między instancjami,
- testów integracyjnych z Testcontainers,
- pełnych dashboardów Grafana dla circuit breakerów i cache,
- alertów symptom-based.

Te elementy należą już do następnych faz: observability, load testing i cloud hardening.
