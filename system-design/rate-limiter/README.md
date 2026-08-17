# Rate Limiter — System Design

Kompleksowy projekt systemu **Rate Limiter** dla API, mikroserwisów i systemów rozproszonych.

Rate Limiter odpowiada na pytanie:

```text
Czy dane żądanie powinno zostać przepuszczone, czy zablokowane?
```

Przykładowa odpowiedź pozytywna:

```json
{
  "allowed": true,
  "remaining": 42,
  "retry_after_ms": null,
  "limit": 100,
  "window_ms": 60000
}
```

Przykładowa odpowiedź negatywna:

```json
{
  "allowed": false,
  "remaining": 0,
  "retry_after_ms": 13700,
  "limit": 100,
  "window_ms": 60000
}
```

---

## Spis treści

1. [Cel systemu](#1-cel-systemu)
2. [Wymagania funkcjonalne](#2-wymagania-funkcjonalne)
3. [Wymagania niefunkcjonalne](#3-wymagania-niefunkcjonalne)
4. [Główne przypadki użycia](#4-główne-przypadki-użycia)
5. [Architektura wysokopoziomowa](#5-architektura-wysokopoziomowa)
6. [Modele wdrożenia](#6-modele-wdrożenia)
7. [Rekomendowana architektura](#7-rekomendowana-architektura)
8. [Komponenty systemu](#8-komponenty-systemu)
9. [Algorytmy rate limitingu](#9-algorytmy-rate-limitingu)
10. [Rekomendowany algorytm](#10-rekomendowany-algorytm)
11. [API Rate Limiter Service](#11-api-rate-limiter-service)
12. [Model danych konfiguracji](#12-model-danych-konfiguracji)
13. [Klucze w Redisie](#13-klucze-w-redisie)
14. [Atomowość](#14-atomowość)
15. [Token Bucket — logika](#15-token-bucket--logika)
16. [Hierarchiczne limity](#16-hierarchiczne-limity)
17. [Skalowanie](#17-skalowanie)
18. [Hot key mitigation](#18-hot-key-mitigation)
19. [Caching konfiguracji](#19-caching-konfiguracji)
20. [Consistency model](#20-consistency-model)
21. [Failure modes](#21-failure-modes)
22. [Multi-region design](#22-multi-region-design)
23. [Quotas vs Rate Limits](#23-quotas-vs-rate-limits)
24. [Event-driven usage accounting](#24-event-driven-usage-accounting)
25. [Observability](#25-observability)
26. [Logging](#26-logging)
27. [Alerty](#27-alerty)
28. [Bezpieczeństwo](#28-bezpieczeństwo)
29. [Przykładowe reguły](#29-przykładowe-reguły)
30. [Request flow](#30-request-flow)
31. [Sekwencja](#31-sekwencja)
32. [Strategia dla 429](#32-strategia-dla-429)
33. [Idempotency i retry](#33-idempotency-i-retry)
34. [Koszt pamięciowy](#34-koszt-pamięciowy)
35. [Optymalizacje wydajności](#35-optymalizacje-wydajności)
36. [Rule matching](#36-rule-matching)
37. [Przykładowy rule resolution](#37-przykładowy-rule-resolution)
38. [Testowanie](#38-testowanie)
39. [Przykładowe SLO](#39-przykładowe-slo)
40. [Admin API](#40-admin-api)
41. [Przykładowy stack technologiczny](#41-przykładowy-stack-technologiczny)
42. [Trade-offy](#42-trade-offy)
43. [Minimalna wersja MVP](#43-minimalna-wersja-mvp)
44. [Produkcyjna wersja docelowa](#44-produkcyjna-wersja-docelowa)
45. [Największe ryzyka](#45-największe-ryzyka)
46. [Proponowana decyzja końcowa](#46-proponowana-decyzja-końcowa)

---

## 1. Cel systemu

**Rate Limiter** ogranicza liczbę żądań wykonywanych przez użytkownika, klienta, API key, IP, tenant lub usługę w określonym czasie.

Przykłady limitów:

```text
100 requestów / minutę / użytkownik
10 000 requestów / godzinę / API key
5 requestów / sekundę / IP
1 000 requestów / dzień / tenant
```

System powinien odpowiadać na pytanie:

```text
Czy to żądanie powinno zostać przepuszczone?
```

Możliwe odpowiedzi:

- `allowed: true` — request może zostać obsłużony,
- `allowed: false` — request powinien zostać odrzucony, zwykle kodem HTTP `429 Too Many Requests`.

---

## 2. Wymagania funkcjonalne

System powinien obsługiwać:

### 2.1 Limity per klucz

Możliwe typy kluczy:

- user ID,
- API key,
- IP,
- tenant ID,
- endpoint,
- metoda HTTP,
- kombinacje, np. `tenant:user:endpoint`.

### 2.2 Różne typy limitów

- per sekunda,
- per minuta,
- per godzina,
- per dzień,
- limity burstowe,
- limity globalne.

### 2.3 Decyzję online

Każde żądanie powinno dostać decyzję:

```text
allow / deny
```

### 2.4 Nagłówki HTTP

System powinien wspierać nagłówki:

```http
X-RateLimit-Limit
X-RateLimit-Remaining
X-RateLimit-Reset
Retry-After
```

### 2.5 Konfigurację limitów

- globalne limity domyślne,
- limity per plan, np. Free, Pro, Enterprise,
- limity per endpoint,
- wyjątki dla konkretnych klientów.

### 2.6 Obsługę wielu algorytmów

- fixed window,
- sliding window,
- token bucket,
- leaky bucket.

### 2.7 Integrację z infrastrukturą

- API Gateway,
- reverse proxy,
- service mesh,
- Envoy,
- NGINX,
- Kong,
- HAProxy,
- własny gateway.

---

## 3. Wymagania niefunkcjonalne

| Obszar | Wymaganie |
|---|---|
| Latencja | bardzo niska, najlepiej `< 5 ms` dla decyzji lokalnej lub Redisowej |
| Dostępność | bardzo wysoka, np. 99.99% |
| Skalowalność | miliony requestów na minutę |
| Spójność | wystarczająca, ale nie zawsze absolutna |
| Odporność | system nie może łatwo blokować całego API |
| Konfigurowalność | zmiany limitów bez redeployu |
| Obserwowalność | metryki, logi, tracing |
| Bezpieczeństwo | brak łatwego obchodzenia limitów |

Rate limiter to komponent na ścieżce krytycznej requestu. Jeżeli jest wolny albo niestabilny, degraduje cały system. Dlatego projekt musi minimalizować zależności i koszt każdej decyzji.

---

## 4. Główne przypadki użycia

### 4.1 Limit per API key

```text
API key: abc123
Limit: 1000 requests / hour
```

Klucz limitera:

```text
rate_limit:api_key:abc123:hour:2026-06-05T14
```

### 4.2 Limit per użytkownik i endpoint

```text
User 42 może wykonać 10 requestów / minutę na /login
```

Klucz:

```text
rate_limit:user:42:endpoint:/login:minute:2026-06-05T14:32
```

### 4.3 Limit globalny

```text
Całe API może obsłużyć maksymalnie 100 000 requestów / minutę
```

Klucz:

```text
rate_limit:global:minute:2026-06-05T14:32
```

### 4.4 Limity hierarchiczne

Jedno żądanie może podlegać kilku limitom naraz:

```text
1. global: 100 000 rpm
2. tenant: 10 000 rpm
3. user: 100 rpm
4. endpoint /payments: 20 rpm
```

Request jest dozwolony tylko wtedy, gdy **wszystkie limity pozwalają**.

---

## 5. Architektura wysokopoziomowa

```text
Client
  |
  v
API Gateway / Load Balancer
  |
  v
Rate Limiter Check
  |
  +--> Config Store
  |
  +--> Fast Counter Store, np. Redis Cluster
  |
  v
Backend Service
```

---

## 6. Modele wdrożenia

### 6.1 Opcja A: Rate limiter w API Gateway

```text
Client -> API Gateway + Rate Limiter -> Services
```

Zalety:

- centralne miejsce kontroli,
- backendy są chronione przed nadmiarem ruchu,
- łatwe egzekwowanie limitów per API key/IP,
- dobre dla publicznych API.

Wady:

- gateway staje się bardziej złożony,
- trudniej o bardzo specyficzne limity biznesowe,
- gateway musi znać identity requestu.

To najczęściej najlepszy wariant dla publicznego API.

### 6.2 Opcja B: Rate limiter jako osobny serwis

```text
Client -> Gateway -> Rate Limiter Service -> Backend
```

Gateway lub backend pyta serwis:

```http
POST /check
Content-Type: application/json

{
  "key": "user:42",
  "rule": "100_per_minute"
}
```

Zalety:

- centralna logika,
- łatwe aktualizacje algorytmów,
- dobre API dla wielu usług,
- łatwiejsze testowanie.

Wady:

- dodatkowy network hop,
- Rate Limiter Service może stać się bottleneckiem,
- trzeba bardzo uważać na dostępność.

Dobre dla większych organizacji i mikroserwisów.

### 6.3 Opcja C: Rate limiter jako biblioteka w każdej usłudze

```text
Client -> Gateway -> Service with embedded rate limiter
```

Zalety:

- brak dodatkowego hopa,
- prosta integracja lokalna,
- dobry dla małych systemów.

Wady:

- duplikacja logiki,
- trudniejsze zarządzanie konfiguracją,
- większe ryzyko niespójności,
- trudniejsze globalne limity.

Dobre dla prostych systemów, ale słabe jako długoterminowa platforma infrastrukturalna.

---

## 7. Rekomendowana architektura

Dla produkcyjnego systemu rekomendowana jest hybryda:

```text
                         +----------------+
                         | Config Service |
                         +----------------+
                                  |
                                  v
Client
  |
  v
API Gateway / Edge Proxy
  |
  v
Local Rate Limiter Module
  |
  +------ hot config cache
  |
  +------ Redis Cluster / Counter Store
  |
  v
Backend Services
```

Decyzja rate-limitowa wykonywana jest **na brzegu**, najlepiej w gatewayu, z szybkim dostępem do Redis Cluster.

Dla części limitów można użyć lokalnego cache’a, ale główne liczniki powinny być w szybkim współdzielonym store.

---

## 8. Komponenty systemu

### 8.1 API Gateway / Enforcement Point

Odpowiada za:

- odczytanie tożsamości klienta,
- zbudowanie klucza limitera,
- pobranie reguł,
- wykonanie checka,
- odrzucenie requestu, jeśli limit przekroczony,
- dodanie nagłówków HTTP.

Przykład odpowiedzi przy przekroczeniu limitu:

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 14
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1717599600
```

### 8.2 Rate Limiter Engine

Logika decyzyjna:

```text
input:
  identity
  endpoint
  method
  tenant
  timestamp

output:
  allowed / denied
  remaining
  retry_after
```

Engine:

1. znajduje pasujące reguły,
2. buduje klucze,
3. wykonuje atomową operację w Redisie,
4. zwraca decyzję.

### 8.3 Config Store

Przechowuje reguły limitów.

Możliwe backendy:

- PostgreSQL,
- DynamoDB,
- etcd,
- Consul,
- własny config service.

Przykładowa reguła:

```json
{
  "id": "free_plan_default",
  "scope": "api_key",
  "plan": "free",
  "limit": 1000,
  "window_ms": 3600000,
  "algorithm": "token_bucket",
  "burst": 100,
  "enabled": true
}
```

### 8.4 Counter Store

Najczęściej:

- Redis,
- Redis Cluster,
- KeyDB,
- Aerospike,
- DynamoDB przy niższych wymaganiach latencji,
- lokalny in-memory store dla prostych przypadków.

Dla wysokiej wydajności najczęściej najlepszym wyborem będzie:

```text
Redis Cluster + Lua scripts
```

Powód: potrzebujemy atomowych operacji typu:

```text
read counter -> increment -> set TTL -> return decision
```

To musi być wykonane atomowo, inaczej pod dużą konkurencją limity będą przeciekać.

---

## 9. Algorytmy rate limitingu

### 9.1 Fixed Window Counter

Najprostszy algorytm.

Limit:

```text
100 requests / minute
```

Dla każdej minuty trzymamy licznik.

Klucz:

```text
rate:user:42:2026-06-05T14:32
```

Operacje:

```text
INCR key
EXPIRE key 60s
```

Jeżeli licznik `<= 100`, request przechodzi.

Zalety:

- bardzo prosty,
- szybki,
- tani pamięciowo,
- łatwy do zaimplementowania w Redisie.

Wady:

- problem na granicy okna.

Przykład problemu:

```text
12:00:59 -> 100 requestów
12:01:00 -> 100 requestów
```

W praktyce klient zrobił 200 requestów w 2 sekundy, mimo limitu 100/min.

Fixed Window jest dobry dla prostych limitów, ale nie jest idealny dla ochrony przed burstami.

### 9.2 Sliding Window Log

Przechowujemy timestamp każdego requestu.

Dla limitu:

```text
100 requests / minute
```

Dla requestu w czasie `T`:

1. usuń wpisy starsze niż `T - 60s`,
2. policz pozostałe wpisy,
3. jeśli `< 100`, dodaj timestamp i pozwól,
4. inaczej zablokuj.

Redis:

```text
ZREMRANGEBYSCORE key 0 now-window
ZCARD key
ZADD key now request_id
EXPIRE key window
```

Zalety:

- bardzo dokładny,
- dobrze radzi sobie z granicą okien.

Wady:

- większe zużycie pamięci,
- koszt rośnie z liczbą requestów,
- trudniejsze skalowanie dla bardzo gorących kluczy.

Dobry dla limitów bezpieczeństwa, np. logowanie, reset hasła, płatności.

### 9.3 Sliding Window Counter

Kompromis między fixed window a sliding log.

Używa dwóch okien:

```text
previous window count
current window count
```

Szacowana liczba requestów:

```text
estimated = current_count + previous_count * overlap_ratio
```

Przykład:

```text
Limit: 100/min
Obecne okno: 30 requestów
Poprzednie okno: 80 requestów
Jesteśmy 25% w obecnym oknie
Overlap poprzedniego okna = 75%

estimated = 30 + 80 * 0.75 = 90
```

Zalety:

- mniej pamięci niż sliding log,
- bardziej sprawiedliwy niż fixed window,
- dobry kompromis produkcyjny.

Wady:

- wynik jest przybliżony,
- nie jest tak dokładny jak sliding log.

To bardzo dobry wybór jako domyślny algorytm API rate limitera.

### 9.4 Token Bucket

Bardzo popularny algorytm dla API.

Każdy klient ma bucket z tokenami.

Parametry:

```text
capacity = 100
refill_rate = 10 tokenów / sekundę
```

Request zużywa token. Jeśli tokeny są dostępne, request przechodzi. Jeśli nie, jest blokowany.

Zalety:

- dobrze obsługuje bursty,
- naturalnie wspiera limity typu sustained rate + burst,
- popularny w gatewayach,
- dobry dla publicznych API.

Wady:

- wymaga przechowywania liczby tokenów i czasu ostatniego uzupełnienia,
- odrobinę bardziej złożony niż fixed window.

Przykład:

```text
Plan Pro:
  sustained: 100 requests / second
  burst: 500 requests
```

To oznacza, że klient może chwilowo wykonać 500 requestów, ale długoterminowo tylko 100 rps.

### 9.5 Leaky Bucket

Requesty trafiają do kolejki, która wypuszcza je stałym tempem.

Zalety:

- wygładza ruch,
- dobre dla systemów wymagających stabilnego przepływu.

Wady:

- wymaga kolejkowania,
- zwiększa latencję,
- mniej nadaje się do prostego allow/deny.

Leaky bucket jest lepszy do **traffic shapingu** niż do klasycznego rate limitingu HTTP.

---

## 10. Rekomendowany algorytm

Dla większości publicznych API:

```text
Token Bucket
```

Dla wrażliwych endpointów bezpieczeństwa:

```text
Sliding Window Log
```

Dla taniego, prostego, globalnego limitowania:

```text
Fixed Window Counter
```

Praktyczna rekomendacja:

| Use case | Algorytm |
|---|---|
| Public API per user/API key | Token Bucket |
| Login / password reset / OTP | Sliding Window Log |
| Globalny limit systemowy | Fixed Window lub Token Bucket |
| Billing/quotas per dzień | Fixed Window |
| Ochrona przed burstami | Token Bucket |
| Bardzo dokładne limity | Sliding Window Log |

Nie należy wybierać jednego algorytmu dla wszystkiego. To częsty błąd w projektach rate limiterów.

---

## 11. API Rate Limiter Service

Jeśli projektujemy osobny serwis, API może wyglądać następująco.

### 11.1 Check request

```http
POST /v1/rate-limit/check
Content-Type: application/json
```

Request:

```json
{
  "subject": {
    "type": "api_key",
    "id": "abc123",
    "tenant_id": "tenant_1",
    "user_id": "user_42",
    "ip": "203.0.113.10"
  },
  "request": {
    "method": "POST",
    "path": "/v1/payments",
    "cost": 1
  },
  "timestamp_ms": 1717599300000
}
```

Response:

```json
{
  "allowed": true,
  "results": [
    {
      "rule_id": "pro_plan_default",
      "allowed": true,
      "limit": 1000,
      "remaining": 981,
      "reset_ms": 1717599600000,
      "retry_after_ms": null
    }
  ]
}
```

### 11.2 Batch check

Dla wydajności warto wspierać batch:

```http
POST /v1/rate-limit/batch-check
Content-Type: application/json
```

Przydatne, gdy jeden request zużywa kilka limitów:

```json
{
  "checks": [
    {
      "key": "tenant:123",
      "rule_id": "tenant_rpm"
    },
    {
      "key": "user:42",
      "rule_id": "user_rpm"
    },
    {
      "key": "endpoint:/payments",
      "rule_id": "payments_rpm"
    }
  ]
}
```

### 11.3 Consume with cost

Nie każdy request musi kosztować `1`.

Przykład:

```text
GET /users -> cost 1
POST /payments -> cost 5
POST /exports -> cost 50
```

Request:

```json
{
  "key": "user:42",
  "rule_id": "exports_limit",
  "cost": 50
}
```

To pozwala limitować drogie operacje mocniej niż tanie.

---

## 12. Model danych konfiguracji

### 12.1 Tabela `rate_limit_rules`

```sql
CREATE TABLE rate_limit_rules (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    scope TEXT NOT NULL,
    algorithm TEXT NOT NULL,
    limit_value INT NOT NULL,
    window_ms BIGINT,
    refill_rate_per_sec DOUBLE PRECISION,
    burst_capacity INT,
    cost INT DEFAULT 1,
    priority INT DEFAULT 100,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### 12.2 Tabela `rate_limit_assignments`

Przypisuje reguły do planów, tenantów, użytkowników, endpointów.

```sql
CREATE TABLE rate_limit_assignments (
    id UUID PRIMARY KEY,
    rule_id UUID NOT NULL REFERENCES rate_limit_rules(id),
    subject_type TEXT NOT NULL,
    subject_id TEXT,
    path_pattern TEXT,
    method TEXT,
    plan TEXT,
    priority INT DEFAULT 100,
    enabled BOOLEAN DEFAULT TRUE
);
```

Przykłady:

```text
rule_id = free_plan_1000_per_hour
subject_type = plan
plan = free

rule_id = login_5_per_minute
path_pattern = /login
method = POST

rule_id = enterprise_custom
subject_type = tenant
subject_id = tenant_123
```

---

## 13. Klucze w Redisie

### 13.1 Fixed Window

```text
rl:{rule_id}:{subject_id}:{window_start}
```

Przykład:

```text
rl:login_5pm:user_42:202606051432
```

Wartość:

```text
3
```

TTL:

```text
window_size + buffer
```

### 13.2 Sliding Window Log

Redis Sorted Set:

```text
rl:{rule_id}:{subject_id}
```

Score:

```text
timestamp_ms
```

Member:

```text
request_id
```

### 13.3 Token Bucket

Redis Hash:

```text
rl:tb:{rule_id}:{subject_id}
```

Pola:

```text
tokens: 47.5
last_refill_ms: 1717599300000
```

TTL:

```text
czas potrzebny do pełnego odnowienia bucketu + buffer
```

---

## 14. Atomowość

To bardzo ważny punkt.

Nie wolno robić tak:

```text
GET counter
if counter < limit:
    INCR counter
```

To jest race condition. Dwa requesty równolegle mogą zobaczyć ten sam licznik i oba przejść.

Poprawnie:

```text
Lua script w Redisie
```

Przykład fixed window w Lua:

```lua
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])
local cost = tonumber(ARGV[3])

local current = tonumber(redis.call("GET", key) or "0")

if current + cost > limit then
  return {0, current, limit - current}
end

local new_value = redis.call("INCRBY", key, cost)

if new_value == cost then
  redis.call("PEXPIRE", key, ttl)
end

return {1, new_value, limit - new_value}
```

---

## 15. Token Bucket — logika

Pseudokod:

```python
def allow_request(key, now_ms, capacity, refill_rate_per_ms, cost):
    bucket = redis.hgetall(key)

    if bucket is empty:
        tokens = capacity
        last_refill_ms = now_ms
    else:
        tokens = float(bucket["tokens"])
        last_refill_ms = int(bucket["last_refill_ms"])

    elapsed = now_ms - last_refill_ms
    refill = elapsed * refill_rate_per_ms

    tokens = min(capacity, tokens + refill)

    if tokens >= cost:
        tokens -= cost
        allowed = True
    else:
        allowed = False

    redis.hmset(key, {
        "tokens": tokens,
        "last_refill_ms": now_ms
    })

    return allowed, tokens
```

W Redisie to również powinno być wykonane przez Lua script.

---

## 16. Hierarchiczne limity

Przykład:

```text
POST /v1/payments
```

Może mieć limity:

```text
global: 100 000 rpm
tenant: 10 000 rpm
user: 100 rpm
endpoint: 20 rpm
```

Naiwne podejście:

```text
Sprawdzamy każdy limit po kolei i konsumujemy tokeny.
```

Problem:

```text
global allowed
tenant allowed
user allowed
endpoint denied
```

Wtedy global/tenant/user już zostały zużyte, mimo że request finalnie został odrzucony.

### 16.1 Opcja 1: Akceptujemy partial consumption

Najprostsze.

Zalety:

- proste,
- szybkie,
- zwykle akceptowalne.

Wady:

- odrzucone requesty mogą konsumować wyższe limity.

Dobre dla większości systemów.

### 16.2 Opcja 2: Check first, consume later

Najpierw sprawdzamy wszystkie limity, potem konsumujemy.

Problem: race condition. Między check i consume stan może się zmienić.

### 16.3 Opcja 3: Atomic multi-key script

W Redisie można wykonać atomowo kilka kluczy, ale w Redis Cluster wszystkie klucze muszą być w tym samym hash slocie.

Można użyć hash tags:

```text
rl:{user_42}:global
rl:{user_42}:tenant
rl:{user_42}:endpoint
```

Problem: globalne limity nie mapują się naturalnie na ten sam slot.

### 16.4 Rekomendacja

Dla praktycznego systemu:

```text
1. Najpierw sprawdź najbardziej szczegółowe limity.
2. Potem bardziej ogólne.
3. Akceptuj partial consumption albo rozdziel enforcement.
```

Kolejność:

```text
endpoint/user -> tenant -> global
```

Jeżeli user przekracza limit endpointu, nie ma sensu konsumować globalnego limitu.

---

## 17. Skalowanie

### 17.1 Skalowanie Rate Limiter Service

Serwis powinien być stateless.

```text
Rate Limiter instances:
  - rl-1
  - rl-2
  - rl-3
  - rl-N
```

Każda instancja korzysta z:

```text
Redis Cluster
Config Cache
Metrics backend
```

Dzięki temu skalowanie to po prostu dodanie instancji.

### 17.2 Skalowanie Redis Cluster

Redis Cluster sharding po kluczu:

```text
rl:{rule_id}:{subject_id}
```

Największy problem: **hot keys**.

Przykłady hot key:

```text
global limit
tenant z ogromnym ruchem
popularny endpoint
```

Hot key może przeciążyć jeden shard.

---

## 18. Hot key mitigation

### 18.1 Local pre-limit

Na każdej instancji gatewaya można mieć lokalny limiter, np.:

```text
global limit: 100 000 rpm
gateway instances: 10
local limit per instance: 10 000 rpm
```

Zalety:

- bardzo szybkie,
- zmniejsza obciążenie Redis,
- dobre dla globalnych limitów.

Wady:

- nierówne rozłożenie ruchu może powodować fałszywe blokady albo przecieki.

### 18.2 Sharded counters

Zamiast jednego licznika:

```text
rl:global:minute
```

używamy wielu:

```text
rl:global:minute:shard:0
rl:global:minute:shard:1
...
rl:global:minute:shard:63
```

Request losowo trafia do jednego shardu.

Problem: aby znać dokładną sumę, trzeba odczytać wszystkie shardy.

Można robić approximate limiting:

```text
limit_per_shard = global_limit / shard_count
```

Albo okresowo agregować.

### 18.3 Approximate rate limiting

Dla globalnych limitów często nie potrzebujesz perfekcyjnej dokładności. Lepiej mieć szybki system, który czasem przepuści 1–2% więcej, niż idealny limiter, który sam stanie się bottleneckiem.

To jest ważny kompromis architektoniczny.

---

## 19. Caching konfiguracji

Nie wolno dla każdego requestu czytać konfiguracji z bazy danych.

Poprawny model:

```text
Config DB -> Config Service -> Gateway local cache
```

Gateway trzyma config w pamięci:

```text
rule_id -> rule
path_pattern -> matching rules
plan -> default rules
tenant -> overrides
```

Odświeżanie:

1. polling co kilka sekund,
2. push przez pub/sub,
3. wersjonowane snapshoty konfiguracji.

Przykład:

```json
{
  "version": 1842,
  "rules": []
}
```

Gateway używa najnowszej znanej wersji.

---

## 20. Consistency model

Rate limiter zwykle nie wymaga absolutnej spójności.

Akceptowalne:

```text
Limit 1000/min czasem przepuści 1003/min
```

Nieakceptowalne:

```text
Limit 1000/min regularnie przepuszcza 2000/min
```

Dla różnych limitów stosujemy różną spójność:

| Typ limitu | Spójność |
|---|---|
| Billing quota | wysoka |
| Security endpoint | wysoka |
| Global overload protection | przybliżona |
| UX-friendly API limits | średnia |
| Abuse prevention | wysoka, ale może być probabilistyczna |

---

## 21. Failure modes

To jedna z najważniejszych części projektu. Rate limiter jest zależnością krytyczną.

### 21.1 Redis niedostępny

Możliwe strategie:

#### Fail-open

Jeśli limiter nie działa, przepuszczamy requesty.

Zalety:

- API pozostaje dostępne.

Wady:

- brak ochrony przed abuse,
- możliwe przeciążenie backendów.

Dobre dla mniej krytycznych limitów.

#### Fail-closed

Jeśli limiter nie działa, blokujemy requesty.

Zalety:

- ochrona backendu.

Wady:

- awaria limitera = awaria API.

Dobre tylko dla bardzo wrażliwych operacji, np. płatności, login, fraud-sensitive endpoints.

#### Degraded local mode

Jeśli Redis padnie, gateway używa lokalnych limitów awaryjnych.

Przykład:

```text
Normalnie: Redis token bucket 1000 rpm
Awaryjnie: local fixed window 500 rpm
```

To często najlepsza strategia.

### 21.2 Config Store niedostępny

Gateway używa ostatniej znanej konfiguracji.

```text
last known good config
```

Jeżeli nie ma żadnej konfiguracji:

- dla public API: użyj conservative defaults,
- dla internal API: można fail-open,
- dla security endpointów: fail-closed lub bardzo niski lokalny limit.

### 21.3 Clock skew

Algorytmy oparte o czas są podatne na różnice zegarów.

Rozwiązania:

- używać czasu Redis servera (`TIME`) zamiast czasu aplikacji,
- synchronizować NTP,
- nie ufać timestampowi od klienta.

Nie wolno używać timestampu przesłanego przez klienta jako źródła prawdy.

---

## 22. Multi-region design

### 22.1 Regional rate limiting

Każdy region ma własny Redis.

```text
EU Gateway -> EU Redis
US Gateway -> US Redis
```

Zalety:

- niska latencja,
- dobra dostępność,
- prosta architektura.

Wady:

- limity globalne mogą przeciekać.

Przykład:

```text
Limit globalny: 1000/min
EU przepuści 1000/min
US przepuści 1000/min
Razem: 2000/min
```

Można podzielić limit:

```text
EU: 500/min
US: 500/min
```

Ale to działa słabo przy nierównym ruchu.

### 22.2 Globalny rate limiter

Wspólny globalny store albo synchronizacja między regionami.

Zalety:

- dokładniejsze limity globalne.

Wady:

- większa latencja,
- większa złożoność,
- podatność na awarie międzyregionalne.

### 22.3 Rekomendacja multi-region

Dla większości API:

```text
Regional limit enforcement + okresowa globalna agregacja
```

Dla billing quotas:

```text
asynchroniczne usage events + dokładne rozliczenie offline/nearline
```

Dla abuse/security:

```text
regionalne twarde limity + globalne sygnały fraud/abuse
```

Nie warto robić perfekcyjnego, globalnie spójnego rate limitera dla każdego requestu, bo koszt i latencja zwykle nie są warte zysku.

---

## 23. Quotas vs Rate Limits

To są różne rzeczy.

### 23.1 Rate limit

```text
100 requestów / minutę
```

Chroni system przed chwilowym przeciążeniem.

### 23.2 Quota

```text
1 000 000 requestów / miesiąc
```

Służy do billingów, planów i ograniczeń długoterminowych.

Rate limiter może obsługiwać oba, ale technicznie warto je rozdzielić:

```text
Realtime rate limit -> Redis
Usage accounting / quotas -> Kafka + DB / warehouse
```

Dla miesięcznych limitów niekoniecznie chcesz trzymać wszystko tylko w Redisie.

---

## 24. Event-driven usage accounting

Każdy zaakceptowany request może emitować event:

```json
{
  "tenant_id": "tenant_123",
  "user_id": "user_42",
  "api_key": "abc123",
  "endpoint": "/v1/payments",
  "cost": 5,
  "timestamp": "2026-06-05T14:32:10Z"
}
```

Pipeline:

```text
Gateway -> Kafka -> Stream Processor -> Usage DB / Warehouse
```

Użycie:

- billing,
- dashboardy,
- analityka,
- wykrywanie abuse,
- miesięczne quotas.

---

## 25. Observability

Metryki:

```text
rate_limiter.requests_total
rate_limiter.allowed_total
rate_limiter.denied_total
rate_limiter.redis_latency_ms
rate_limiter.config_version
rate_limiter.fail_open_total
rate_limiter.fail_closed_total
rate_limiter.local_fallback_total
rate_limiter.hot_key_detected_total
```

Wymiary:

```text
rule_id
tenant_id
endpoint
method
region
decision
algorithm
```

Uwaga: z wymiarami trzeba uważać. `user_id` jako label metryki może zabić Prometheusa przez wysoką kardynalność.

Dla user-level danych lepsze są logi albo eventy, nie metryki.

---

## 26. Logging

Log dla zablokowanego requestu:

```json
{
  "event": "rate_limit_denied",
  "rule_id": "free_plan_default",
  "subject_type": "api_key",
  "subject_hash": "sha256:...",
  "endpoint": "/v1/payments",
  "method": "POST",
  "limit": 100,
  "remaining": 0,
  "retry_after_ms": 12000,
  "region": "eu-central-1"
}
```

Nie należy logować surowych API keys ani pełnych danych osobowych. Lepiej używać hashy.

---

## 27. Alerty

Przykładowe alerty:

```text
Redis latency p99 > 20 ms przez 5 minut
Rate limiter error rate > 1%
Fail-open count > 0
Denied traffic wzrósł 5x względem baseline
Config version stale > 5 minut
Hot key CPU usage high
429 rate dla konkretnego endpointu przekracza próg
```

---

## 28. Bezpieczeństwo

### 28.1 Identity extraction

Nie można bezwarunkowo ufać nagłówkom typu:

```http
X-Forwarded-For
X-User-ID
```

Jeżeli gateway stoi za load balancerem, trzeba jasno określić trusted proxies.

Dla IP rate limitingu:

```text
client_ip = first untrusted IP in X-Forwarded-For chain
```

### 28.2 API key hashing

API keys nie powinny być przechowywane ani logowane plaintextem.

Klucz limitera może używać hash:

```text
sha256(api_key)
```

### 28.3 Bypass rules

Trzeba uważać na allowlisty:

```text
internal_service = unlimited
```

To częsty błąd. Lepiej dać wysokie, ale skończone limity.

### 28.4 Distributed abuse

IP-based limiter jest słaby przeciwko botnetom. User/API-key limiter jest zwykle lepszy.

Najlepiej stosować kombinację:

```text
IP + user + API key + tenant + endpoint
```

---

## 29. Przykładowe reguły

```yaml
rules:
  - id: global_api_limit
    scope: global
    algorithm: token_bucket
    refill_rate_per_sec: 5000
    burst_capacity: 20000

  - id: free_plan_default
    scope: api_key
    plan: free
    algorithm: token_bucket
    refill_rate_per_sec: 1
    burst_capacity: 60

  - id: pro_plan_default
    scope: api_key
    plan: pro
    algorithm: token_bucket
    refill_rate_per_sec: 50
    burst_capacity: 1000

  - id: login_ip_limit
    scope: ip
    path: /login
    method: POST
    algorithm: sliding_window_log
    limit: 5
    window_ms: 60000

  - id: password_reset_user_limit
    scope: user
    path: /password/reset
    method: POST
    algorithm: sliding_window_log
    limit: 3
    window_ms: 3600000
```

---

## 30. Request flow

```text
1. Client wysyła request.
2. Gateway uwierzytelnia request albo odczytuje API key.
3. Gateway buduje kontekst:
   - api_key_hash
   - user_id
   - tenant_id
   - ip
   - endpoint
   - method
4. Gateway znajduje pasujące reguły w lokalnym config cache.
5. Dla każdej reguły wykonuje check/consume.
6. Jeśli dowolna reguła odmawia:
   - zwraca 429
   - ustawia Retry-After
   - loguje event
7. Jeśli wszystkie pozwalają:
   - przepuszcza request do backendu
   - opcjonalnie emituje usage event
```

---

## 31. Sekwencja

```text
Client
  |
  | HTTP request
  v
Gateway
  |
  | identify subject
  v
Rate Limiter Module
  |
  | get matching rules from local cache
  v
Config Cache
  |
  | atomic consume
  v
Redis Cluster
  |
  | allowed / denied
  v
Gateway
  |
  | allowed -> backend
  | denied -> 429
  v
Backend Service
```

---

## 32. Strategia dla 429

Odpowiedź:

```json
{
  "error": "rate_limit_exceeded",
  "message": "Too many requests. Please retry later.",
  "retry_after_ms": 12000
}
```

Nagłówki:

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 12
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1717599600
```

Dla wielu limitów naraz, `Retry-After` powinien pochodzić z najbardziej restrykcyjnego limitu, czyli tego, który pozwoli najpóźniej.

---

## 33. Idempotency i retry

Rate limiter może przypadkowo karać retry klienta.

Przykład:

```text
Klient wysłał POST.
Backend przetworzył request.
Połączenie padło.
Klient retry.
Rate limiter liczy retry jako drugi request.
```

Rozwiązania:

1. wspierać `Idempotency-Key`,
2. nie naliczać pełnego kosztu dla duplikatu,
3. deduplikować po `request_id` przez krótki czas.

Ostrożnie: deduplikacja każdego requestu zwiększa koszt pamięciowy.

---

## 34. Koszt pamięciowy

### 34.1 Fixed window

Jeden licznik per aktywny klucz per okno.

```text
active_keys * active_rules
```

Jeżeli masz:

```text
1 000 000 aktywnych API keys
3 reguły
```

To potencjalnie:

```text
3 000 000 kluczy
```

Redis sobie z tym poradzi, ale trzeba pilnować TTL.

### 34.2 Sliding window log

Pamięć zależy od liczby requestów.

```text
active_requests_in_window
```

Dla 1 mln requestów/min to może być dużo. Dlatego sliding log trzeba stosować selektywnie.

### 34.3 Token bucket

Jeden hash/string per aktywny subject/rule.

Najlepszy kompromis pamięciowy.

---

## 35. Optymalizacje wydajności

1. **Lua scripts**
   - atomowość,
   - mniej round-tripów.

2. **Pipeline Redis**
   - gdy sprawdzamy wiele limitów.

3. **Local config cache**
   - zero DB lookupów na request.

4. **Local emergency limiter**
   - fallback bez Redisa.

5. **Batch API**
   - mniej network overhead.

6. **Precomputed rule matching**
   - szybkie mapowanie endpoint → reguły.

7. **Approximate global limits**
   - mniej hot key problemów.

8. **Connection pooling**
   - Redis połączenia utrzymywane stale.

---

## 36. Rule matching

Reguły mogą mieć różne priorytety:

```text
1. tenant-specific override
2. API key-specific override
3. endpoint-specific rule
4. plan default
5. global default
```

Przykład:

```yaml
priority:
  tenant_override: 10
  api_key_override: 20
  endpoint_specific: 30
  plan_default: 100
  global_default: 1000
```

Trzeba jasno zdefiniować, czy reguły:

1. się sumują,
2. najbardziej specyficzna wygrywa,
3. wszystkie obowiązują naraz.

Rekomendacja:

```text
Reguły bezpieczeństwa i globalne zawsze obowiązują.
Reguły planowe mogą być override’owane przez tenant/API-key-specific config.
```

---

## 37. Przykładowy rule resolution

Dla requestu:

```text
tenant = t1
plan = pro
user = u42
endpoint = POST /v1/payments
```

System wybiera:

```text
global_api_limit
tenant_t1_limit
pro_plan_default
payments_endpoint_limit
user_u42_custom_limit, jeśli istnieje
```

Potem wykonuje check dla każdej aktywnej reguły.

---

## 38. Testowanie

### 38.1 Unit tests

- fixed window increments,
- token refill,
- boundary conditions,
- Retry-After calculation,
- cost > 1,
- disabled rules,
- expired keys.

### 38.2 Concurrency tests

Scenariusz:

```text
limit = 100
parallel requests = 1000
allowed powinno być <= 100
```

To testuje atomowość.

### 38.3 Load tests

Testy:

```text
100k rps
1M aktywnych kluczy
hot tenant
Redis shard failure
config reload
```

### 38.4 Chaos tests

- Redis timeout,
- Redis partial outage,
- config service niedostępny,
- gateway restart,
- opóźniony config update,
- clock skew.

---

## 39. Przykładowe SLO

```text
Rate limiter check availability: 99.99%
p99 decision latency: < 10 ms
p95 decision latency: < 3 ms
Incorrect allow/deny rate: < 0.1%
Config propagation delay: < 10 seconds
Redis error rate: < 0.01%
```

---

## 40. Admin API

Do zarządzania regułami:

```http
POST /admin/rules
PATCH /admin/rules/{id}
GET /admin/rules
DELETE /admin/rules/{id}

POST /admin/assignments
GET /admin/effective-rules?tenant_id=t1&user_id=u42&path=/v1/payments
```

Przydatny endpoint:

```http
GET /admin/debug/effective-limit
```

Request:

```json
{
  "tenant_id": "t1",
  "user_id": "u42",
  "api_key": "abc",
  "method": "POST",
  "path": "/v1/payments"
}
```

Response:

```json
{
  "matched_rules": [
    "global_api_limit",
    "tenant_t1_limit",
    "pro_plan_default",
    "payments_endpoint_limit"
  ]
}
```

To bardzo pomaga w debugowaniu.

---

## 41. Przykładowy stack technologiczny

### 41.1 Wariant prosty

```text
API Gateway: NGINX / Kong / własny middleware
Counter store: Redis
Config DB: PostgreSQL
Metrics: Prometheus + Grafana
Logs: Loki / Elasticsearch
```

### 41.2 Wariant większy

```text
Gateway: Envoy / Kong / custom Go service
Rate Limiter Service: Go / Rust / Java
Counter Store: Redis Cluster
Config Store: PostgreSQL + config service
Events: Kafka
Analytics: ClickHouse / BigQuery / Snowflake
Metrics: Prometheus
Tracing: OpenTelemetry
```

---

## 42. Trade-offy

| Decyzja | Plus | Minus |
|---|---|---|
| Redis | szybki, prosty | hot keys, memory pressure |
| Token bucket | dobry dla API | mniej intuicyjny niż fixed window |
| Sliding log | dokładny | drogi pamięciowo |
| Gateway enforcement | chroni backend | gateway bardziej złożony |
| Fail-open | dostępność | ryzyko abuse |
| Fail-closed | bezpieczeństwo | ryzyko outage |
| Multi-region local | niska latencja | limit może przeciekać |
| Global consistency | dokładność | latencja i złożoność |

---

## 43. Minimalna wersja MVP

Na start warto zbudować:

```text
1. Gateway middleware
2. Redis fixed window albo token bucket
3. Config w pliku / PostgreSQL
4. Lokalne cache’owanie configu
5. Nagłówki rate limit
6. Metryki allowed/denied/latency
7. Fail-open dla większości endpointów
8. Fail-closed/local fallback dla login/security endpoints
```

MVP nie powinno zaczynać od multi-region, sliding log dla wszystkiego i skomplikowanego rule engine’u. To może szybko przerosnąć projekt.

---

## 44. Produkcyjna wersja docelowa

Docelowo:

```text
- API Gateway enforcement
- Redis Cluster
- Lua scripts
- Token bucket jako domyślny algorytm
- Sliding window log dla endpointów bezpieczeństwa
- Config Service z wersjonowanymi snapshotami
- Local config cache
- Local fallback limiter
- Usage events do Kafka
- Dashboardy i alerty
- Admin/debug API
- Multi-region regional enforcement
- Approximate global limits
```

---

## 45. Największe ryzyka

### 45.1 Rate limiter jako single point of failure

Jeżeli każdy request zależy od niego, musi mieć fallback.

### 45.2 Hot keys

Globalne i tenantowe limity mogą przeciążyć jeden shard.

### 45.3 Zbyt dokładne projektowanie

Perfekcyjna globalna spójność jest droga. Często niepotrzebna.

### 45.4 Zła identyfikacja klienta

IP-based limiting bez poprawnej obsługi proxy jest łatwy do obejścia albo może blokować niewinnych użytkowników.

### 45.5 Brak obserwowalności

Bez dobrych metryk trudno odróżnić realny abuse od źle ustawionego limitu.

---

## 46. Proponowana decyzja końcowa

Dla solidnego projektu Rate Limiter rekomendowany wybór:

```text
Enforcement: API Gateway
Storage: Redis Cluster
Default algorithm: Token Bucket
Security endpoints: Sliding Window Log
Global protection: approximate/local + Redis
Config: PostgreSQL + Config Service + local cache
Atomicity: Redis Lua scripts
Fallback: local degraded limiter
Usage accounting: Kafka events
Observability: Prometheus + logs + tracing
```

To daje dobry balans między:

```text
wydajnością,
dokładnością,
prostotą,
skalowalnością,
odpornością na awarie.
```

Najważniejsza decyzja projektowa: **nie próbować robić idealnie dokładnego limitera dla każdego przypadku**.

Dla API trafficu zwykle lepszy jest szybki, odporny i lekko przybliżony system niż perfekcyjny system, który stanie się bottleneckiem.

---

## Podsumowanie

Rate Limiter powinien być:

- szybki,
- odporny na awarie,
- konfigurowalny,
- obserwowalny,
- możliwie prosty w krytycznej ścieżce requestu.

Najlepszy praktyczny design to połączenie:

- enforcementu w API Gateway,
- Redis Cluster jako szybkiego counter store,
- Lua scripts dla atomowości,
- Token Bucket jako domyślnego algorytmu,
- Sliding Window Log dla endpointów bezpieczeństwa,
- lokalnego fallbacku,
- wersjonowanej konfiguracji,
- eventów użycia do billingów i analityki.
