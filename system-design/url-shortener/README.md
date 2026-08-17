# URL Shortener — System Design

Kompleksowy projekt systemu skracania URL-i w wersji produkcyjnej. Dokument opisuje wymagania, architekturę, API, model danych, generowanie short code, cache, skalowanie, bezpieczeństwo, analytics, obserwowalność oraz kompromisy projektowe.

---

## 1. Cel systemu

System skracania URL-i pozwala użytkownikowi zamienić długi adres, np.

```text
https://example.com/products/category/very/long/path?utm_source=...
```

na krótki link:

```text
https://sho.rt/aB92xK
```

Po wejściu w krótki link użytkownik powinien zostać szybko przekierowany na oryginalny URL.

System powinien obsługiwać:

```text
Create short URL
Resolve short URL
Optional custom aliases
Expiration dates
Analytics / click tracking
Abuse prevention
High read throughput
Low latency redirects
```

---

## 2. Założenia

Przyjmujemy realistyczną skalę:

```text
100 mln nowych linków rocznie
10 mld redirectów miesięcznie
Read-heavy system: redirectów jest dużo więcej niż tworzenia linków
Średni długi URL: 500 znaków
Krótki kod: 7–10 znaków
Retencja danych: kilka lat
```

Stosunek odczyt/zapis może wynosić np.:

```text
100:1 albo 1000:1
```

To istotne, bo architektura musi być zoptymalizowana głównie pod szybkie redirecty, nie pod tworzenie linków.

---

## 3. Wymagania funkcjonalne

### Podstawowe

System musi umożliwiać:

1. Utworzenie krótkiego linku dla długiego URL-a.
2. Przekierowanie z krótkiego linku do oryginalnego URL-a.
3. Obsługę wygasania linków.
4. Obsługę błędów, np. link nie istnieje, wygasł, został zablokowany.
5. Opcjonalnie: custom alias, np. `sho.rt/promo2026`.

### Rozszerzone

Dodatkowo warto przewidzieć:

```text
Analytics: liczba kliknięć, kraj, referrer, device
Rate limiting
User accounts
Publiczne i prywatne linki
Blokowanie złośliwych URL-i
QR code generation
Bulk URL creation
API keys dla klientów biznesowych
```

---

## 4. Wymagania niefunkcjonalne

Najważniejsze:

```text
Bardzo niska latencja redirectu
Wysoka dostępność
Skalowalność horyzontalna
Odporność na awarie
Brak kolizji short code
Bezpieczeństwo przed abuse
Dobra obserwowalność
```

Docelowo redirect powinien być bardzo szybki:

```text
p50 < 20 ms
p95 < 100 ms
p99 < 200 ms
```

W praktyce redirect endpoint powinien być najprostszy i najmocniej zoptymalizowanym elementem systemu.

---

## 5. Główne komponenty systemu

Architektura wysokopoziomowa:

```text
Client
  |
  v
CDN / Edge
  |
  v
Load Balancer
  |
  v
API Gateway / Reverse Proxy
  |
  +-------------------------+
  |                         |
  v                         v
URL Service              Redirect Service
  |                         |
  v                         v
Primary DB               Cache
  |                         |
  v                         v
Analytics Queue          Primary DB fallback
  |
  v
Analytics Workers
  |
  v
Analytics DB / Data Warehouse
```

System można logicznie podzielić na kilka usług:

```text
URL Service       - tworzenie i zarządzanie linkami
Redirect Service  - szybkie przekierowania
Analytics Service - zbieranie i agregowanie kliknięć
Admin Service     - moderation, abuse, blokady
Auth Service      - użytkownicy, API keys
```

W mniejszym projekcie można zacząć od jednego backendu modularnego, ale granice domen warto mieć od początku.

---

## 6. API

### 6.1 Create short URL

```http
POST /api/v1/urls
Content-Type: application/json
Authorization: Bearer <token>
```

Request:

```json
{
  "longUrl": "https://example.com/very/long/url",
  "customAlias": "promo2026",
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

Response:

```json
{
  "id": "url_123",
  "shortUrl": "https://sho.rt/promo2026",
  "shortCode": "promo2026",
  "longUrl": "https://example.com/very/long/url",
  "expiresAt": "2026-12-31T23:59:59Z",
  "createdAt": "2026-06-05T10:00:00Z"
}
```

Dla aliasów generowanych automatycznie:

```json
{
  "longUrl": "https://example.com/very/long/url"
}
```

Response:

```json
{
  "shortUrl": "https://sho.rt/aB92xK7"
}
```

### 6.2 Redirect

```http
GET /{shortCode}
```

Przykład:

```http
GET /aB92xK7
```

Response:

```http
302 Found
Location: https://example.com/very/long/url
```

Dla trwałego linku można rozważyć `301`, ale ostrożnie. W praktyce często lepiej używać `302` albo `307`, bo `301` może być agresywnie cache’owany przez przeglądarki i utrudnia zmianę docelowego URL-a.

Najbezpieczniejszy domyślny wybór:

```text
302 Found
```

### 6.3 Get URL details

```http
GET /api/v1/urls/{shortCode}
```

Response:

```json
{
  "shortCode": "aB92xK7",
  "longUrl": "https://example.com/very/long/url",
  "createdAt": "2026-06-05T10:00:00Z",
  "expiresAt": null,
  "status": "ACTIVE"
}
```

### 6.4 Delete / deactivate URL

```http
DELETE /api/v1/urls/{shortCode}
```

Albo miękkie usunięcie:

```http
PATCH /api/v1/urls/{shortCode}
```

```json
{
  "status": "DISABLED"
}
```

W systemie produkcyjnym lepiej robić soft delete niż fizyczne usunięcie, przynajmniej dla linków publicznych, bo ułatwia audyt i bezpieczeństwo.

### 6.5 Analytics

```http
GET /api/v1/urls/{shortCode}/analytics
```

Response:

```json
{
  "shortCode": "aB92xK7",
  "totalClicks": 123456,
  "uniqueVisitors": 45678,
  "topCountries": [
    { "country": "PL", "clicks": 50000 },
    { "country": "DE", "clicks": 20000 }
  ],
  "topReferrers": [
    { "referrer": "google.com", "clicks": 30000 }
  ]
}
```

---

## 7. Model danych

### 7.1 Tabela `urls`

Relacyjny model może wyglądać tak:

```sql
CREATE TABLE urls (
    id BIGINT PRIMARY KEY,
    short_code VARCHAR(16) NOT NULL UNIQUE,
    long_url TEXT NOT NULL,
    user_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NULL,
    metadata JSONB NULL
);
```

Indeksy:

```sql
CREATE UNIQUE INDEX idx_urls_short_code ON urls(short_code);
CREATE INDEX idx_urls_user_id ON urls(user_id);
CREATE INDEX idx_urls_expires_at ON urls(expires_at);
CREATE INDEX idx_urls_status ON urls(status);
```

Najważniejszy indeks:

```text
short_code -> long_url
```

To lookup wykonywany przy każdym redirectcie.

### 7.2 Tabela `click_events`

Surowe eventy kliknięć:

```sql
CREATE TABLE click_events (
    id BIGINT PRIMARY KEY,
    short_code VARCHAR(16) NOT NULL,
    clicked_at TIMESTAMP NOT NULL,
    ip_hash VARCHAR(128),
    user_agent TEXT,
    referrer TEXT,
    country VARCHAR(8),
    device_type VARCHAR(32)
);
```

Przy bardzo dużej skali nie należy trzymać surowych eventów wyłącznie w klasycznej bazie transakcyjnej. Lepiej wysyłać je do kolejki, a potem do systemu analitycznego.

### 7.3 Agregaty analytics

```sql
CREATE TABLE url_daily_stats (
    short_code VARCHAR(16) NOT NULL,
    date DATE NOT NULL,
    clicks BIGINT NOT NULL,
    unique_visitors BIGINT,
    PRIMARY KEY (short_code, date)
);
```

Do szybkiego dashboardu nie powinno się za każdym razem liczyć wszystkiego z miliardów eventów.

---

## 8. Generowanie short code

To jedna z najważniejszych decyzji projektowych.

### Opcja A: auto-increment ID + Base62

Generujemy rosnące ID:

```text
12500001
```

Kodujemy je do Base62:

```text
aZ93kL
```

Base62 używa:

```text
0-9
a-z
A-Z
```

Czyli łącznie:

```text
62 znaki
```

Liczba możliwych kodów:

```text
62^7 ≈ 3.5 biliona
62^8 ≈ 218 bilionów
```

Dla 100 mln linków rocznie 7 znaków wystarcza na bardzo długo.

Zalety:

```text
Brak kolizji
Szybkie generowanie
Krótkie kody
Proste mapowanie ID -> code
```

Wady:

```text
Sekwencyjność
Możliwość zgadywania kolejnych linków
Wymaga centralnego generatora ID albo rozproszonego ID generatora
```

Żeby ograniczyć zgadywalność, można:

```text
dodać salt
użyć bijective hash
wymieszać ID
użyć Snowflake ID
```

### Opcja B: losowy kod Base62

Generujemy losowy string, np. 7–10 znaków.

Zalety:

```text
Trudniejszy do zgadnięcia
Brak widocznej sekwencji
Łatwy w systemie rozproszonym
```

Wady:

```text
Ryzyko kolizji
Trzeba sprawdzać unikalność w bazie
Przy dużym zapełnieniu przestrzeni rośnie koszt retry
```

Przykład:

```pseudo
function generateShortCode(length):
    alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    code = randomString(alphabet, length)
    if exists(code):
        retry
    return code
```

Dla małego i średniego systemu to jest bardzo dobre rozwiązanie. Dla ogromnej skali preferowany jest jednak ID + Base62 albo pre-generated key pool.

### Opcja C: pre-generated key pool

Osobny system generuje wcześniej pulę dostępnych kodów:

```text
aB92xK7
x93KsL2
Pq82Lm9
```

Przy tworzeniu linku URL Service pobiera wolny kod z puli.

Zalety:

```text
Bardzo szybkie tworzenie linków
Brak kolizji w runtime
Możliwość batchowego generowania
```

Wady:

```text
Większa złożoność
Trzeba zarządzać pulą
Trzeba zapewnić brak podwójnego wydania kodu
```

To podejście jest dobre przy bardzo dużej skali.

### Rekomendacja

Dla solidnego systemu produkcyjnego rekomendowane podejście:

```text
Snowflake-style distributed ID -> Base62 -> opcjonalne mieszanie ID
```

Dlaczego?

```text
Nie ma kolizji
Działa horyzontalnie
Jest szybkie
Nie wymaga losowych retry
Dobrze skaluje się globalnie
```

Przykład:

```text
ID: 847392847239
Base62: k9Az21B
Short URL: https://sho.rt/k9Az21B
```

---

## 9. Redirect flow

Najważniejsza ścieżka systemu:

```text
User clicks short URL
  |
  v
CDN / Edge receives request
  |
  v
Redirect Service
  |
  v
Check cache: shortCode -> longUrl
  |
  +-- Cache hit --> return 302 immediately
  |
  +-- Cache miss --> query DB
                  |
                  v
              validate status / expiry
                  |
                  v
              populate cache
                  |
                  v
              return 302
```

Pseudo-kod:

```pseudo
GET /{shortCode}:
    url = cache.get(shortCode)

    if url is null:
        record = db.findByShortCode(shortCode)

        if record is null:
            return 404

        if record.status != ACTIVE:
            return 410

        if record.expiresAt != null and record.expiresAt < now:
            return 410

        cache.set(shortCode, record.longUrl, ttl=24h)
        url = record.longUrl

    enqueueClickEvent(shortCode, requestMetadata)

    return redirect(302, url)
```

Ważna rzecz: analytics nie może blokować redirectu. Event kliknięcia powinien być wysłany asynchronicznie.

---

## 10. Cache

Cache jest krytyczny.

Najczęstszy lookup:

```text
short_code -> long_url
```

Do tego nadaje się:

```text
Redis
Memcached
CDN edge cache
Local in-memory cache
```

### Strategia cache

Dla aktywnych linków:

```text
TTL: 1h - 24h
```

Dla bardzo popularnych linków:

```text
dłuższy TTL
cache warming
edge caching
```

Dla linków nieistniejących warto rozważyć negative caching:

```text
shortCode -> NOT_FOUND
TTL: 1-5 min
```

Chroni to bazę przed spamem i brute force.

### Problem z aktualizacją URL-a

Jeżeli system pozwala edytować docelowy URL, musi unieważniać cache:

```text
update DB
delete cache key
publish invalidation event
```

Jeśli linki są niemutowalne, system jest prostszy i bezpieczniejszy.

Rekomendacja:

```text
Domyślnie short URL jest niemutowalny.
Zmiany tylko dla kont biznesowych albo adminów.
```

---

## 11. Baza danych

### Opcja 1: PostgreSQL

Dobre na start i średnią skalę.

Zalety:

```text
Silna spójność
Unikalne indeksy
Transakcje
Prosty development
Dobre wsparcie dla analytics w małej skali
```

Wady:

```text
Trzeba uważać na ogromny ruch read
Wymaga read replicas
Sharding przy dużej skali jest trudniejszy
```

Architektura:

```text
Primary PostgreSQL - writes
Read replicas      - reads fallback
Redis              - hot reads
```

### Opcja 2: DynamoDB / Cassandra / ScyllaDB

Dobre dla bardzo dużej skali redirectów.

Primary access pattern:

```text
PK: short_code
Value: long_url, status, expires_at
```

Zalety:

```text
Bardzo duża skalowalność
Niska latencja przy key-value lookup
Łatwy sharding
```

Wady:

```text
Mniej wygodne zapytania ad hoc
Inny model danych
Trudniejsza lokalna praca i migracje
```

### Rekomendacja

Dla MVP:

```text
PostgreSQL + Redis
```

Dla dużej skali:

```text
DynamoDB / Cassandra / ScyllaDB + Redis / CDN cache
```

Najrozsądniejszy etapowy wybór:

```text
Start: PostgreSQL
Scale: PostgreSQL read replicas + Redis
Massive scale: KV store sharded by short_code
```

---

## 12. Sharding

Jeśli system jest bardzo duży, można shardować po:

```text
short_code
hash(short_code)
user_id
creation_time
```

Najlepszy wybór dla redirectów:

```text
hash(short_code)
```

Bo główne zapytanie wygląda tak:

```text
find by short_code
```

Przykład:

```pseudo
shard = hash(shortCode) % numberOfShards
```

Każdy shard przechowuje podzbiór linków.

Problem: jeśli short code generowany jest sekwencyjnie, można przypadkiem przeciążyć jeden shard. Dlatego lepiej shardować po hashu short code, nie po samym ID.

---

## 13. Analytics design

Nie należy robić tego synchronicznie:

```text
Redirect request -> write click event to DB -> redirect
```

To zwiększyłoby latencję i ryzyko awarii.

Lepszy flow:

```text
Redirect Service
  |
  v
Kafka / Kinesis / Pub/Sub
  |
  v
Stream processors
  |
  +--> Real-time counters
  +--> Data warehouse
  +--> Fraud detection
```

Event:

```json
{
  "shortCode": "aB92xK7",
  "clickedAt": "2026-06-05T10:00:00Z",
  "ipHash": "abc123",
  "userAgent": "Mozilla/5.0...",
  "referrer": "https://google.com",
  "country": "PL"
}
```

### Liczniki kliknięć

Można użyć:

```text
Redis counters dla realtime
Batch aggregation dla dokładnych statystyk
ClickHouse / BigQuery / Snowflake dla analityki
```

Podejście praktyczne:

```text
Redis: szybki licznik total_clicks
Kafka: surowe eventy
ClickHouse: analityka czasowa
PostgreSQL: agregaty dzienne do dashboardu
```

---

## 14. Rate limiting

System URL shortener jest podatny na abuse:

```text
spam
phishing
malware
masowe generowanie linków
brute-force short code
DDoS na popularne linki
```

Rate limiting powinien działać na kilku poziomach:

```text
IP
user_id
API key
short_code
country / ASN w skrajnych przypadkach
```

Przykład limitów:

```text
Anonymous create URL: 10/min/IP
Logged-in create URL: 100/min/user
API clients: zależnie od planu
Redirect: dużo wyższe limity, ale z ochroną DDoS
```

Dla redirectów trzeba uważać, żeby nie zablokować viralowego linku.

---

## 15. Security

### Walidacja URL

Należy sprawdzić:

```text
Czy URL ma poprawny format
Czy scheme to http albo https
Czy nie prowadzi do localhost / private IP
Czy nie zawiera podejrzanych payloadów
Czy domena nie jest na blockliście
```

Blokować należy m.in.:

```text
http://localhost:8080
http://127.0.0.1
http://169.254.169.254
http://10.0.0.1
file://...
javascript:alert(1)
```

To chroni przed SSRF i nadużyciami.

### Abuse detection

Można dodać:

```text
Google Safe Browsing / podobny mechanizm reputacyjny
Własne blocklisty domen
Manual moderation
Automatyczne flagowanie linków z wysokim bounce/spam rate
```

### Preview page

Dla podejrzanych linków zamiast natychmiastowego redirectu można pokazać stronę ostrzegawczą:

```text
Ten link prowadzi do zewnętrznej strony:
https://suspicious-domain.example
Kontynuuj?
```

### Custom aliases

Custom aliases powinny być walidowane:

```text
brak obraźliwych słów
brak podszywania się pod marki
brak aliasów systemowych typu api, admin, login
brak znaków specjalnych
minimalna i maksymalna długość
```

Reserved aliases:

```text
api
admin
login
signup
health
metrics
robots.txt
favicon.ico
```

---

## 16. Statusy linków

Proponowany enum:

```text
ACTIVE
EXPIRED
DISABLED
BLOCKED
DELETED
PENDING_REVIEW
```

Redirect behavior:

```text
ACTIVE          -> 302
EXPIRED         -> 410 Gone
DISABLED        -> 410 Gone
BLOCKED         -> warning page albo 403
DELETED         -> 404 albo 410
PENDING_REVIEW  -> 403 / warning
```

`410 Gone` jest lepsze niż `404`, jeśli link kiedyś istniał, ale już nie jest dostępny.

---

## 17. Wygasanie linków

Są dwa podejścia.

### Lazy expiration

Sprawdzamy `expires_at` podczas redirectu.

```pseudo
if expiresAt < now:
    return 410
```

Zalety:

```text
Proste
Nie wymaga jobów
```

Wady:

```text
Stare dane zostają w bazie
Cache musi uwzględniać expires_at
```

### Background expiration job

Proces okresowo oznacza wygasłe linki jako `EXPIRED`.

```text
cron / worker co kilka minut
```

Najlepiej połączyć oba podejścia:

```text
Lazy check dla poprawności
Background job dla porządku i sprzątania
```

---

## 18. Consistency

Tworzenie linków wymaga silnej spójności dla `short_code`.

```text
short_code musi być unikalny
```

Redirect może tolerować eventual consistency, ale ostrożnie.

Problem:

```text
User tworzy link
Od razu klika link
Read replica jeszcze nie ma danych
Dostaje 404
```

Rozwiązania:

```text
Po utworzeniu linku zapisuj go od razu do cache
Redirect fallback do primary DB przez krótki czas
Read-after-write consistency dla nowych linków
```

Najprościej:

```text
Create URL:
  write to DB
  write to Redis
  return short URL
```

Dzięki temu świeży link działa od razu, nawet jeśli replika ma lag.

---

## 19. High availability

Dla produkcji:

```text
Multiple app instances
Multiple availability zones
Redis cluster albo managed Redis with failover
DB replication
Backups
CDN in front
Health checks
Circuit breakers
Graceful degradation
```

Najważniejsza zasada:

```text
Redirect Service nie powinien zależeć od Analytics Service.
```

Jeśli analytics padnie, redirecty nadal mają działać.

---

## 20. Failure scenarios

### Cache down

Fallback:

```text
Redirect Service czyta z DB
Latencja rośnie
System nadal działa
```

Ryzyko:

```text
DB może zostać przeciążona
```

Mitigacja:

```text
rate limiting
read replicas
local in-memory cache
circuit breaker
```

### DB down

Jeśli cache działa:

```text
Popularne linki nadal działają
Nowe linki nie mogą być tworzone
Cache miss może nie działać
```

Można rozważyć:

```text
read-only degraded mode
stale cache serving
```

### Queue down

Redirecty nie powinny padać.

```text
Drop analytics event
Buffer lokalny
Retry z limitem
```

Lepiej stracić część analytics niż zepsuć redirect.

### Redis stale data

Jeśli link został zablokowany, a Redis nadal ma stary URL, to problem bezpieczeństwa.

Dla blokad trzeba robić natychmiastową invalidację:

```text
Admin blocks URL
DB update
Cache delete
Publish invalidation event
Edge cache purge
```

Dla złośliwych linków bezpieczeństwo jest ważniejsze niż wydajność.

---

## 21. CDN i edge

CDN może pomóc przy bardzo popularnych linkach.

Ale jest haczyk: jeśli każdy redirect ma generować analytics, pełne cache’owanie na CDN może omijać backend.

### Strategia A: backend always hit

Każdy redirect trafia do Redirect Service.

Zalety:

```text
Dokładne analytics
Prostsza kontrola bezpieczeństwa
```

Wady:

```text
Większy koszt
Większa latencja
```

### Strategia B: CDN caches redirects

CDN cache’uje odpowiedzi `302`.

Zalety:

```text
Bardzo szybkie redirecty
Mniejsze obciążenie backendu
```

Wady:

```text
Trudniejsze analytics
Trudniejsze blokowanie linków
Ryzyko stale redirectów
```

### Strategia C: edge worker

Edge wykonuje lookup w edge KV albo cache, a analytics wysyła asynchronicznie.

Zalety:

```text
Bardzo niska latencja
Globalna skala
```

Wady:

```text
Większa złożoność
Vendor lock-in
Trudniejsze consistency
```

Rekomendacja:

```text
MVP: backend + Redis
Duża skala: CDN + edge caching dla hot links
Bardzo duża skala: edge workers + regional KV/cache
```

---

## 22. Multi-region design

Dla globalnego systemu:

```text
Users worldwide
Low latency redirects
High availability during regional failures
```

Możliwa architektura:

```text
Global DNS / Anycast
  |
  v
Nearest region
  |
  v
Regional Redirect Service
  |
  v
Regional cache
  |
  v
Global replicated KV store
```

Writes są trudniejsze niż reads.

### Single write region

```text
Wszystkie create URL idą do jednej głównej regii
Redirecty obsługiwane globalnie
Dane replikowane do innych regionów
```

Prostsze, ale większa latencja dla tworzenia linków.

### Multi-write

```text
Każdy region może tworzyć linki
ID generator musi być globalnie unikalny
Dane replikują się między regionami
```

Trudniejsze, ale lepsze dla dużej skali.

Dla większości systemów rekomendacja:

```text
Single write region + global read replicas/cache
```

Dopiero później multi-write.

---

## 23. Observability

Należy mierzyć:

### Redirect metrics

```text
redirect_requests_total
redirect_latency_p50/p95/p99
cache_hit_ratio
db_lookup_latency
404_rate
410_rate
blocked_redirects
top_short_codes
```

### Create URL metrics

```text
url_create_requests_total
url_create_errors_total
short_code_collision_count
custom_alias_conflicts
rate_limited_requests
```

### Analytics metrics

```text
queue_lag
events_ingested_per_second
events_dropped
aggregation_delay
```

### Alerts

Alerty na:

```text
wysoki 5xx rate
spadek cache hit ratio
wzrost DB latency
wzrost 404/blocked rate
queue lag
Redis unavailable
DB replication lag
```

---

## 24. Logging

Dla redirectów logi muszą być lekkie, bo wolumen jest ogromny.

Nie należy logować pełnego IP ani pełnych danych osobowych. Lepiej:

```text
request_id
short_code
status_code
latency_ms
cache_hit
country
hashed_ip
user_agent_family
```

Unikać:

```text
pełne IP
pełne user-agent w nieskończonej retencji
wrażliwe query params
```

---

## 25. Privacy i compliance

URL-e mogą zawierać dane wrażliwe w query parametrach.

Przykład:

```text
https://example.com/reset-password?token=secret
```

System powinien:

```text
nie pokazywać long URL publicznie bez autoryzacji
maskować query params w panelu, jeśli trzeba
ograniczać retencję click eventów
hashować IP
umożliwić usunięcie danych użytkownika
```

Dla analytics:

```text
IP -> hash z rotowanym saltem
GeoIP liczony chwilowo, potem IP usuwane
User-Agent parsowany do device/browser, raw UA usuwany po czasie
```

---

## 26. Idempotency

Tworzenie URL-i przez API powinno wspierać idempotency key.

```http
Idempotency-Key: 8e3b1c0f-...
```

Przydatne, gdy klient retry’uje request po timeoutcie.

Bez tego można przypadkiem utworzyć wiele krótkich linków dla tego samego długiego URL-a.

Tabela:

```sql
CREATE TABLE idempotency_keys (
    key VARCHAR(128) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    response JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

---

## 27. Deduplication

Pytanie projektowe: czy ten sam długi URL powinien zawsze dostać ten sam short URL?

### Bez deduplikacji

Każde wywołanie tworzy nowy short URL.

Zalety:

```text
Proste
Każda kampania może mieć osobne analytics
```

Wady:

```text
Więcej rekordów
```

### Dedup per user

Ten sam user + ten sam long URL może dostać ten sam short URL.

Zalety:

```text
Mniej duplikatów
```

Wady:

```text
Mniej elastyczne kampanie
Trudniejsze analytics
```

Rekomendacja:

```text
Nie deduplikować domyślnie.
Pozwolić klientowi wymusić reuse opcjonalnym parametrem.
```

Przykład:

```json
{
  "longUrl": "https://example.com",
  "reuseExisting": true
}
```

---

## 28. Custom aliases

Przy custom alias trzeba zrobić atomic insert.

```sql
INSERT INTO urls(short_code, long_url, ...)
VALUES ('promo2026', 'https://example.com', ...)
```

Jeżeli alias istnieje:

```http
409 Conflict
```

Response:

```json
{
  "error": "CUSTOM_ALIAS_ALREADY_EXISTS"
}
```

Custom alias powinien mieć ograniczenia:

```text
min 3 znaki
max 32 znaki
[a-zA-Z0-9-_]
brak aliasów zarezerwowanych
case sensitivity jasno określone
```

Rekomendacja:

```text
Short code generowany automatycznie: case-sensitive Base62
Custom alias: najlepiej case-insensitive albo lower-case only
```

Dlaczego? Bo `Promo`, `promo` i `PROMO` jako różne aliasy to proszenie się o błędy użytkowników i phishing.

---

## 29. HTTP status codes

Dla API:

```text
201 Created       - URL utworzony
400 Bad Request   - niepoprawny URL
401 Unauthorized  - brak auth
403 Forbidden     - brak uprawnień
404 Not Found     - short code nie istnieje
409 Conflict      - alias zajęty
410 Gone          - link wygasł/usunięty
429 Too Many Requests - rate limit
500 Internal Server Error
```

Dla redirect:

```text
302 Found       - domyślny redirect
307 Temporary Redirect - jeśli chcesz zachować metodę HTTP
301 Moved Permanently - ostrożnie, dla permanentnych publicznych linków
404 Not Found
410 Gone
403 Forbidden
```

---

## 30. Algorytm Base62

Przykład:

```pseudo
alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

function encodeBase62(number):
    if number == 0:
        return alphabet[0]

    result = ""

    while number > 0:
        remainder = number % 62
        result = alphabet[remainder] + result
        number = number / 62

    return result
```

Dla ID:

```text
100000000 -> 6LAze
```

Można paddingować do minimalnej długości:

```text
0006LAze
```

Ale padding może wyglądać gorzej. Lepsze jest mieszanie ID lub rozpoczęcie od większego offsetu.

---

## 31. Capacity estimation

Załóżmy:

```text
100 mln URL-i rocznie
Retencja: 5 lat
Łącznie: 500 mln URL-i
```

Rozmiar jednego rekordu:

```text
short_code: ~16 B
long_url: ~500 B średnio
metadata/status/timestamps: ~200 B
index overhead: ~100-300 B
```

Szacunkowo:

```text
~1 KB na URL
500 mln URL-i ≈ 500 GB danych podstawowych
z indeksami: 1-2 TB
```

To jest wykonalne, ale wymaga przemyślanej bazy, replikacji i backupów.

Redirecty:

```text
10 mld / miesiąc
≈ 333 mln / dzień
≈ 3 850 / sek średnio
```

Peak może być 10–20x większy:

```text
40k - 80k redirectów/s
```

To już wymaga cache i skalowania horyzontalnego.

---

## 32. Hot links

Niektóre linki mogą być ekstremalnie popularne.

Przykład:

```text
jeden link w viralowej kampanii
miliony wejść w krótkim czasie
```

Mitigacje:

```text
Redis cache
local in-memory cache
CDN cache
edge workers
request coalescing na cache miss
rate limiting tylko dla nadużyć, nie dla normalnego viral traffic
```

Local cache w Redirect Service może trzymać np. top 100k linków przez kilka minut.

---

## 33. Cache stampede

Problem:

```text
Popularny link wypada z cache
Tysiące requestów naraz uderzają w DB
```

Rozwiązania:

```text
TTL jitter
single-flight / request coalescing
background refresh
stale-while-revalidate
```

Przykład:

```text
Cache TTL: 24h ± losowe 10%
```

---

## 34. Deployment

Można wdrożyć w Kubernetes albo prostszym managed środowisku.

Komponenty:

```text
redirect-service
url-service
analytics-worker
admin-service
redis
postgres / dynamodb
kafka / pubsub
clickhouse / warehouse
```

Deployment strategy:

```text
rolling deploy
blue-green dla krytycznych zmian
canary dla Redirect Service
automatyczny rollback po wzroście 5xx/latency
```

---

## 35. CI/CD

Pipeline:

```text
lint
unit tests
integration tests
contract tests
security scan
migration check
load tests dla Redirect Service
deploy to staging
canary production
full rollout
```

Szczególnie ważne testy:

```text
short code uniqueness
redirect correctness
expiration
cache invalidation
custom alias conflicts
rate limiting
blocked URL behavior
```

---

## 36. Backup i disaster recovery

Dla danych URL:

```text
regular backups
point-in-time recovery
cross-region replication
restore drills
```

RPO/RTO:

```text
RPO: kilka minut
RTO: < 1h dla pełnego restore
```

Dla redirectów można utrzymać częściową dostępność przez cache nawet podczas awarii DB.

---

## 37. Proponowany stack technologiczny

### MVP / średnia skala

```text
Backend: Java/Kotlin Spring Boot, Go, Node.js/NestJS albo Python/FastAPI
DB: PostgreSQL
Cache: Redis
Queue: Kafka / RabbitMQ / cloud Pub/Sub
Analytics: ClickHouse albo PostgreSQL aggregates na start
Infra: Docker + Kubernetes albo managed platform
```

### Duża skala

```text
Redirect Service: Go / Rust / Java
Storage: DynamoDB / Cassandra / ScyllaDB
Cache: Redis Cluster + CDN
Queue: Kafka / Kinesis / Pub/Sub
Analytics: ClickHouse / BigQuery / Snowflake
Edge: Cloudflare Workers / Fastly Compute / Lambda@Edge
```

Preferencja:

```text
Go + Redis + PostgreSQL na start
Kafka + ClickHouse dla analytics
DynamoDB/Scylla dopiero przy naprawdę dużej skali
```

Nie warto przepłacać za skomplikowany distributed KV store, dopóki zwykły Postgres z Redisem wystarcza.

---

## 38. Prosty diagram architektury

```text
                         +----------------+
                         |     Client     |
                         +--------+-------+
                                  |
                                  v
                         +----------------+
                         |   CDN / WAF    |
                         +--------+-------+
                                  |
                                  v
                         +----------------+
                         | Load Balancer  |
                         +--------+-------+
                                  |
             +--------------------+--------------------+
             |                                         |
             v                                         v
    +------------------+                     +------------------+
    |  URL Service     |                     | Redirect Service |
    +--------+---------+                     +--------+---------+
             |                                        |
             v                                        v
    +------------------+                     +------------------+
    | Primary Database |<--------------------|      Redis       |
    +--------+---------+                     +------------------+
             |
             v
    +------------------+
    | Outbox / Events  |
    +--------+---------+
             |
             v
    +------------------+
    | Kafka / Queue    |
    +--------+---------+
             |
             v
    +------------------+
    | Analytics Worker |
    +--------+---------+
             |
             v
    +------------------+
    | Analytics Store  |
    +------------------+
```

---

## 39. Endpoint redirectu — najważniejsze optymalizacje

Dla `GET /{shortCode}`:

```text
Nie robić ciężkiego auth
Nie robić synchronicznego zapisu analytics
Nie parsować niepotrzebnie całego requestu
Nie wykonywać wielu zapytań do DB
Nie czekać na zewnętrzne API bezpieczeństwa
Nie logować zbyt dużo synchronicznie
```

Ten endpoint powinien robić prawie wyłącznie:

```text
lookup
validation
async event
redirect
```

---

## 40. Najważniejsze kompromisy projektowe

### 301 vs 302

Rekomendacja:

```text
302 domyślnie
```

Powód:

```text
większa kontrola
łatwiejsze blokowanie
bezpieczniejsze przy zmianach
```

### Random code vs Base62 ID

Rekomendacja:

```text
Base62 z distributed ID
```

Powód:

```text
brak kolizji i dobra skalowalność
```

### Relacyjna DB vs NoSQL

Rekomendacja:

```text
PostgreSQL na start, KV/NoSQL przy ogromnej skali
```

Powód:

```text
prostszy development, mniej złożoności operacyjnej
```

### Analytics sync vs async

Rekomendacja:

```text
async zawsze
```

Powód:

```text
redirect ma być szybki i odporny na awarie analytics
```

### Editable links vs immutable links

Rekomendacja:

```text
immutable domyślnie
```

Powód:

```text
łatwiejszy cache, mniej ryzyk bezpieczeństwa
```

---

## 41. Minimalny MVP

Na początek wystarczy:

```text
POST /api/v1/urls
GET /{shortCode}
PostgreSQL
Redis
Base62 ID
Basic rate limiting
Basic analytics counter
Expiration support
```

MVP architecture:

```text
Monolith backend
PostgreSQL
Redis
Background worker
```

To jest sensowniejsze niż od razu mikroserwisy.

---

## 42. Produkcyjna wersja docelowa

Docelowo:

```text
Separate Redirect Service
Separate URL Management Service
Redis Cluster
Primary DB + read replicas albo distributed KV
Kafka for events
ClickHouse for analytics
WAF + abuse detection
Admin moderation panel
Multi-region read path
CDN / edge cache for hot links
```

---

## 43. Proponowana kolejność budowy

### Etap 1

```text
URL create
Redirect
PostgreSQL
Base62
Basic validation
```

### Etap 2

```text
Redis cache
Expiration
Custom aliases
Rate limiting
```

### Etap 3

```text
Async analytics
Click counters
Dashboard
Admin blocking
```

### Etap 4

```text
Queue
Advanced analytics
Abuse detection
Read replicas
```

### Etap 5

```text
CDN / edge
Multi-region
Distributed storage
Enterprise API
```

---

## 44. Najważniejsze ryzyka

Największe ryzyka w tym systemie to nie samo skracanie URL-i, tylko:

```text
phishing i malware
nadużycia custom aliasów
wysoka skala redirectów
cache invalidation dla zablokowanych linków
dokładność analytics
ochrona prywatności
hot links
brute-force discovery
```

Technicznie URL shortener wygląda prosto, ale produkcyjnie jest to system z dużym naciskiem na bezpieczeństwo, latency i abuse prevention.

---

## 45. Finalna rekomendowana architektura

Najlepszy kompromis dla solidnego projektu:

```text
Backend:
  URL Service
  Redirect Service
  Analytics Worker

Storage:
  PostgreSQL jako primary DB
  Redis jako cache
  Kafka/PubSub jako kolejka eventów
  ClickHouse jako analytics store

Short code:
  distributed ID -> Base62
  custom alias jako osobny path z unikalnym constraintem

Redirect:
  Redis first
  DB fallback
  async analytics event
  302 response

Security:
  URL validation
  blocklists
  rate limiting
  WAF
  admin moderation
  cache invalidation for blocked links

Scalability:
  horizontal app scaling
  read replicas
  Redis cluster
  CDN/edge for hot links
  eventual move to KV store if needed
```

Najważniejsza decyzja projektowa: **nie komplikować startu mikroserwisami i globalnym NoSQL-em, dopóki skala tego nie wymusza**. Dobrze zaprojektowany `PostgreSQL + Redis + async analytics` obsłuży bardzo dużo, a przejście do bardziej rozproszonej architektury można zrobić później, jeśli granice domen i access patterns są od początku dobrze zaprojektowane.
