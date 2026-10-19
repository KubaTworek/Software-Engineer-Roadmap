# Distributed Rate Limiter

<!-- reading-guide:start -->
## Ścieżki czytania

| Cel czytania | Punkt wejścia |
| --- | --- |
| Teoria i model problemu | [1. Problem, wymagania i budżet](#1-problem-wymagania-i-budżet) |
| Implementacja i jej dowody | [Mapa implementacji](#mapa-implementacji) |
| Uruchomienie i eksperymenty | [15. Uruchomienie](#15-uruchomienie) |
| Ograniczenia i scenariusze awarii | [24. Granice gotowości produkcyjnej](#24-granice-gotowości-produkcyjnej) |

Wspólne mechanizmy i ich testy znajdziesz w [indeksie pojęć backend-engineering](../../backend-engineering/README.md#globalny-indeks-pojęć). Tutaj śledź ich konsekwencje dla opisywanego systemu.
Diagramy docelowe i warianty rozwoju czytaj razem z mapą implementacji; sam opis architektury nie oznacza, że wszystkie jej komponenty są uruchamiane w laboratorium.
<!-- reading-guide:end -->

Edukacyjne studium systemu, który podejmuje decyzję `allow/deny` przed wykonaniem
chronionej operacji. Projekt łączy teorię algorytmów z wykonywalną implementacją
Spring Boot + Redis i pokazuje, gdzie kończą się gwarancje pojedynczego skryptu,
a zaczynają decyzje architektoniczne: awarie, wiele regionów, zaufanie do
nagłówków, dynamiczna konfiguracja, quota i obserwowalność.

## Najpierw uruchom

**Najważniejszy niezmiennik:** dla jednego bucketu współbieżne żądania nie mogą
łącznie zużyć więcej tokenów, niż bucket zawiera po uwzględnieniu refill.

**Krytyczny przepływ:** HTTP → wiarygodna tożsamość → dopasowanie wszystkich
polityk → atomowy skrypt Redis per bucket → agregacja decyzji → nagłówki/`429`.

**Rekomendowany test:**

```powershell
.\mvnw.cmd -Pinfrastructure-tests -Dit.test=RedisTokenBucketInfrastructureTest verify
```

Test uruchamia prawdziwy Redis w Testcontainers i dowodzi, że przy 200
równoległych próbach bucket o pojemności 50 przepuszcza dokładnie 50 operacji.

**Świadome ograniczenie:** atomowość obowiązuje jeden bucket. Reguła globalna,
tenantowa i endpointowa mogą leżeć w różnych hash slotach, dlatego cały zestaw
polityk nie jest jedną transakcją. Próba odrzucona przez późniejszą politykę
zużywa tokeny wcześniej sprawdzonych polityk. Jest to jawna semantyka ochronna,
nie przypadkowy efekt implementacji.

## Czego uczy laboratorium

Po przejściu projektu powinieneś umieć odpowiedzieć na pytania:

- czym różnią się fixed window, sliding window, token bucket, GCRA i leaky bucket;
- dlaczego `GET → obliczenie → SET` nie jest poprawnym limiterem rozproszonym;
- dlaczego źródło czasu powinno znajdować się przy stanie limitera;
- jak tworzyć hierarchię limitów global/user/tenant/plan/endpoint;
- czy odrzucona próba ma konsumować budżet i jakie są konsekwencje decyzji;
- kiedy awaria Redisa wymaga fail-open, fail-closed albo lokalnego fallbacku;
- dlaczego regionalny limiter nie daje ścisłego limitu globalnego;
- dlaczego rate limit, quota i ledger billingowy są trzema różnymi pojęciami;
- które nagłówki można uznać za tożsamość oraz jak poprawnie czytać
  `X-Forwarded-For`;
- jak mierzyć limiter bez tworzenia metryk o nieograniczonej cardinality.

## Mapa implementacji

| Element | Rola | Gwarancja / granica |
| --- | --- | --- |
| `RateLimitFilter` | enforcement point HTTP | zwraca nagłówki limitu lub `429` |
| `RequestIdentityResolver` + `GatewayIdentityVerifier` | buduje zaufaną tożsamość | trusted peer oraz opcjonalny świeży podpis HMAC |
| `RuleMatcher` | wybiera polityki | polityki są kumulatywne, priority ustala kolejność |
| `RedisTokenBucketLimiter` | data plane | atomowe zużycie jednego bucketu |
| `token_bucket.lua` | stan i czas w Redisie | `TIME`, refill, consume i TTL w jednym skrypcie |
| `RedisAvailabilityGate` | ochrona zależności | po trzech awariach przez 2 s omija wolny Redis |
| `LocalFallbackLimiter` | degradacja lokalna | chroni instancję, nie globalny ruch |
| `DynamicConfigService` | symulacja control plane | snapshot, walidacja, wersja, `If-Match`, lokalny audit |
| `JdbcControlPlaneStore` + `RuleReplica` | trwały control plane | profil `durable`, tombstone, rollback jako nowa wersja i config lag |
| `QuotaService` | estymacja usage online | atomowy licznik miesiąca; to nie ledger billingowy |
| `UsageEventPublisher` | telemetria asynchroniczna | best effort, błąd Kafki nie zmienia decyzji |
| `JdbcUsageOutbox` + `UsageOutboxRelay` | trwały receipt/outbox | idempotencja, lease, reclaim, bounded retry, DLQ i reconciliation |
| `GlobalTokenLeaseLab` | model multi-region | bounded exposure i fencing starego właściciela |
| `RateLimitAlgorithmLab` | modele dydaktyczne | porównanie algorytmów bez frameworka |

Oznaczenia używane w kodzie i dokumentacji:

- **naive** — celowy kontrprzykład, który narusza niezmiennik;
- **correct** — rozwiązanie poprawne w określonej granicy;
- **simulation** — deterministyczny model do nauki, nie komponent wdrożeniowy;
- **production boundary** — miejsce wymagające decyzji zależnej od środowiska.

## 1. Problem, wymagania i budżet

Limiter chroni ograniczony zasób: CPU, pulę połączeń, partnera zewnętrznego,
kosztowny endpoint lub uczciwy podział pojemności między klientów. Nie jest
zamiennikiem autoryzacji, WAF-a ani kontroli współbieżności wewnątrz operacji.

Typowe wymagania funkcjonalne:

- limit per IP, klucz API, użytkownik, tenant, plan i endpoint;
- koszt ważony, np. eksport kosztuje więcej niż odczyt profilu;
- burst do pojemności bucketu i kontrolowany refill;
- jednoznaczne `429 Too Many Requests` oraz `Retry-After`;
- zmiana reguł bez restartu data plane.

Wymagania niefunkcjonalne są równie ważne:

- bardzo mała latencja decyzji, bo limiter jest na ścieżce każdego requestu;
- brak pojedynczego globalnego hot key, jeśli ruch jest wysoki;
- przewidywalne zachowanie przy timeoutach i awarii store;
- ograniczona cardinality metryk i logów;
- możliwość oszacowania błędu limitu w architekturze multi-region.

Przykładowy rachunek capacity, który należy wykonać przed wdrożeniem:

```text
100 000 RPS × 3 pasujące polityki = 300 000 operacji bucket/s
p99 aplikacji = 120 ms, budżet limitera = 5 ms
1 mln aktywnych principals × ~200 B stanu = ~200 MB + narzut Redis
```

To przybliżenie. Należy dodać ruch administracyjny, retry, replikację, zapas na
failover i realny rozmiar kluczy. Najpierw ustala się wymagany throughput i SLO,
dopiero później wybiera topologię.

## 2. Algorytmy

| Algorytm | Stan | Burst | Dokładność | Typowe zastosowanie |
| --- | ---: | --- | --- | --- |
| Fixed window | licznik + okno | duży na granicy okna | niska | prosty, tani limit |
| Sliding window log | timestamp każdej próby | kontrolowany | wysoka | mały ruch, audyt |
| Sliding window counter | dwa liczniki | mały | przybliżona | kompromis pamięć/dokładność |
| Token bucket | tokeny + czas refill | jawnie do `capacity` | dobra | API i ruch burstowy |
| GCRA | theoretical arrival time | przez tolerancję | dobra | zwarty stan i precyzyjne spacing |
| Leaky bucket | kolejka + tempo odpływu | buforuje | wygładza output | shaping, nie tylko policing |

Wszystkie modele są wykonywalne w `RateLimitAlgorithmLabTest`. Produkcyjny data
plane implementuje celowo tylko Token Bucket, aby nie udawać, że przełącznik
enum automatycznie zapewnia semantykę pozostałych algorytmów.

### Fixed window

Licznik dotyczy przedziału, np. `12:00:00–12:00:59`. Jest tani, ale klient może
wykonać cały limit pod koniec jednego okna i ponownie na początku następnego.
W krótkim przedziale przejdzie prawie `2 × limit`. Dodatkowo naiwny rozdział
`check()` i `increment()` pozwala wielu wątkom zobaczyć ten sam wolny slot.

### Sliding window log

Przechowuje timestamp każdej próby i usuwa starsze niż `now - window`. Daje
intuicyjną dokładność, ale zużycie pamięci rośnie z liczbą requestów. W Redisie
często używa sorted set, co oznacza więcej pamięci i pracy niż prosty licznik.

### Sliding window counter

Interpoluje wynik poprzedniego i bieżącego okna:

```text
estimated = current + previous × remaining_fraction_of_previous_window
```

Nie pamięta każdej próby, ale wynik jest przybliżeniem. Błąd trzeba zaakceptować
jako część kontraktu, a nie ukrywać jako szczegół techniczny.

### Token bucket

Bucket ma `capacity = C`, tempo odnawiania `r` tokenów/s i koszt operacji `k`.
Dla czasu `t`:

```text
refilled = min(C, tokens_previous + (t - t_previous) × r)
allow    = refilled >= k
tokens'  = allow ? refilled - k : refilled
```

`capacity` określa dozwolony burst, a `refill rate` długoterminowy throughput.
To dwie niezależne decyzje. Limit `60/min` nie musi znaczyć burstu 60; można
ustawić `capacity=10`, `refill=1/s`.

### GCRA

Generic Cell Rate Algorithm przechowuje jeden theoretical arrival time. Kolejna
próba jest dopuszczona, jeśli nie przychodzi wcześniej niż TAT pomniejszony o
tolerancję burstu. Model daje semantykę zbliżoną do ciągle uzupełnianego bucketu,
ale wymaga tylko jednego skalara. Test pokazuje burst dwóch żądań i późniejsze
odnawianie rytmu. Produkcyjna ścieżka nadal celowo używa Token Bucket.

### Leaky bucket

Modeluje kolejkę opróżnianą w stałym tempie. Token bucket odpowiada głównie na
„czy dopuścić?”, leaky bucket może odpowiedzieć „kiedy wysłać?”. Jeśli nie ma
kontrolowanej kolejki, limit jej rozmiaru i anulowania, łatwo zamienić shaping w
nieograniczony bufor opóźnionych prac.

## 3. Atomowość i współbieżność

Naiwna sekwencja:

```text
GET bucket
oblicz refill i decyzję
SET bucket
```

nie jest poprawna. Dwa requesty mogą odczytać ten sam stan i oba zużyć ostatni
token. Lua przenosi odczyt, obliczenie, zapis i TTL do jednej atomowej operacji
serwera Redis. Test infrastrukturalny sprawdza zachowanie przy realnej
współbieżności, czego mock Redisa nie jest w stanie dowieść.

Atomowość skryptu nie oznacza atomowości całego requestu biznesowego. Token może
zostać zużyty, a downstream później zwróci `500`. Zwrot tokenu bywa gorszy niż
jego utrata: retry i duplikaty mogą zwiększyć budżet. Polityka musi rozstrzygnąć,
czy limitujemy próby, zaakceptowane requesty czy ukończone operacje. Ten projekt
limituje **próby przyjęcia do chronionego systemu**.

## 4. Czas, precision i TTL

Wiele instancji aplikacji może mieć clock skew. Gdy każda przekazuje własny
timestamp, cofnięty zegar opóźni refill, a wyprzedzony może wyprodukować tokeny.
Skrypt używa `redis.call('TIME')`, więc stan i jego zegar mają tę samą granicę.

Redis `TIME` nie jest zegarem monotonicznym i wciąż może zostać skorygowany.
W granicy jednego store zmniejsza jednak rozbieżność klientów. Produkcyjny
system powinien monitorować NTP i jawnie zdecydować, jaki ujemny elapsed jest
dozwolony. TTL wynosi co najmniej 60 s i około dwa czasy pełnego refill, aby
nieaktywne principals nie zajmowały pamięci bez końca.

Tokeny mogą być ułamkowe. Skrypt zachowuje precision refill, a klient prezentuje
`remaining` jako liczbę całkowitą. Nagłówek jest informacją orientacyjną; jedyną
wiążącą odpowiedzią jest decyzja aktualnej atomowej operacji.

## 5. Klucze Redis i sharding

Przykładowy klucz:

```text
rl:{user:user-123}:eu-central-1:user-limit
```

- `principal` izoluje użytkownika/tenanta/klucz;
- region koduje semantykę regionalną;
- rule ID rozdziela polityki;
- `{...}` jest hash tagiem Redis Cluster.

Klucz globalny jest z definicji hot key. Przy bardzo wysokim ruchu trzeba go
shardować i zaakceptować przybliżenie, rezerwować lokalne porcje globalnego
budżetu albo użyć hierarchicznego limitera. Ścisła globalna serializacja może
stać się droższa niż zasób, który chroni.

Projekt nie uruchamia „udawanego klastra” kilku niepołączonych procesów Redis.
Docker Compose zawiera świadomie pojedynczy Redis do lokalnego laboratorium.
Prawdziwy Redis Cluster wymaga inicjalizacji slotów, obsługi failover, topology
refresh i testów migracji slotu.

## 6. Hierarchia polityk

Jedno żądanie może spełnić kilka reguł:

```text
global ∩ plan ∩ tenant ∩ user ∩ endpoint
```

Reguły w projekcie są **kumulatywne**: wszystkie muszą dopuścić request.
`priority` ustala deterministyczną kolejność oceny, nie oznacza override.
Nazwy reguł nie używają więc mylącego słowa `override`.

Data plane konsumuje każdą pasującą politykę nawet wtedy, gdy jedna odmówiła.
Takie attempt accounting chroni globalne i tenantowe budżety przed darmowym
spamem do już wyczerpanego endpointu. Ceną jest zużycie wcześniejszych bucketów
przez request finalnie odrzucony. Alternatywy to:

- stop on first deny — tańsze, ale kolejność zmienia accounting;
- peek then commit — wymaga rezerwacji i obsługi wyścigów;
- jeden wielokluczowy skrypt — tylko dla kluczy w tym samym hash slocie;
- admission token/saga — większa złożoność i nowe failure windows.

Walidator odrzuca zduplikowane ID, niepełny scope, niepoprawny refill oraz
`cost > capacity`, który tworzyłby regułę nigdy nieprzepuszczającą ruchu.

Wzorce ścieżek są celowo małe i jednoznaczne:

- `/api/users` — exact;
- `/api/*` — dokładnie jeden segment;
- `/api/**` — całe poddrzewo, ale nie `/apix`.

W dużym systemie warto współdzielić matcher z gatewayem lub kompilować reguły do
jednego standardu, aby routing i limitowanie nie interpretowały wzorców inaczej.

## 7. Kontrakt HTTP

Dla decyzji ustawiane są nagłówki `RateLimit-Limit`, `RateLimit-Remaining` oraz
kompatybilne nagłówki `X-*`. `RateLimit-Reset` pojawia się tylko wtedy, gdy
decyzja zna dodatni czas ponownego dopuszczenia; wartość `0` dla zaakceptowanego
requestu fałszywie sugerowałaby pełne odnowienie wszystkich bucketów.
Odrzucenie zwraca:

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 12
Content-Type: application/problem+json

{"type":"urn:problem:rate-limit-exceeded","title":"Rate limit exceeded","status":429,
 "error":"rate_limit_exceeded","retryAfterSeconds":12}
```

Przy wielu politykach pojedyncza para `limit/remaining` jest uproszczeniem.
Implementacja prezentuje najbardziej restrykcyjną wartość i liczbę ocenionych
reguł. API diagnostyczne może pokazać szczegóły, ale publiczna odpowiedź nie
powinna ujawniać identyfikatorów wewnętrznych polityk.

Brak pasującej reguły oznacza default allow. W produkcji należy zdecydować, czy
nieznany endpoint ma być fail-open, czy ma otrzymać bezpieczny limit domyślny.

## 8. Tożsamość i granice zaufania

Limiter per user jest poprawny tylko wtedy, gdy user ID jest wiarygodne. Klient
publiczny może dowolnie sfałszować `X-User-Id`, `X-Tenant-Id` i `X-Plan`.
Projekt honoruje te nagłówki wyłącznie, gdy bezpośredni peer należy do listy
trusted proxies. W przeciwnym razie plan spada do `FREE`, a identyfikatory
user/tenant są pomijane.

Sama prywatna sieć nie jest dowodem tożsamości gatewaya. Opcja
`require-signed-identity-headers` wymaga świeżego timestampu i podpisu HMAC nad
metodą, ścieżką, tenantem, userem oraz planem. Chroni to przed podszyciem przez
inny workload i prostym replay. Jest to laboratorium granicy zaufania; mTLS lub
workload identity pozostają preferowanym mechanizmem platformowym.

`X-Forwarded-For` jest analizowany od prawej strony: pomijamy znane proxy i
wybieramy najbliższy niezaufany hop. Implementacja obsługuje pełny IPv4/IPv6
CIDR i ogranicza długość nagłówka. Czytanie pierwszego elementu z lewej pozwala
atakującemu dopisać własny adres.

Klucz API nie trafia do bucketu ani logu. Identyfikator jest wyliczany przez
`HMAC-SHA-256(secret, apiKey)`. Zwykły hash umożliwia offline guessing kluczy o
małej entropii. Rotacja sekretu HMAC zmienia wszystkie identyfikatory, dlatego
produkcja potrzebuje wersji klucza i kontrolowanej migracji.

Admin API wymaga `X-Admin-Token`, porównuje go w stałym czasie i akceptuje
poprzednie tokeny podczas rotacji. Profil `prod` nie startuje z sekretami demo.
To minimalna granica laboratorium; w praktyce stosuje się osobny internal
gateway, workload identity, mTLS/RBAC, audyt trwały i rate limit administracji.

## 9. Awarie i degradacja

| Strategia | Dostępność | Ochrona | Odpowiednia dla |
| --- | --- | --- | --- |
| `FAIL_OPEN` | wysoka | słaba podczas awarii | endpoint bezpieczny, degradacja akceptowalna |
| `FAIL_CLOSED` | niska | silna | płatność, eksport, kosztowny downstream |
| `LOCAL_FALLBACK` | średnia | per instancja | większość zwykłego API |

Lokalny fallback liczy koszt operacji, ma ograniczone okna, sprząta stare wpisy
i ma twarde `max-buckets`. Po wyczerpaniu cardinality odmawia utworzenia nowego
bucketu zamiast ryzykować OOM. Nie zachowuje jednak globalnej gwarancji: przy `n` instancjach efektywny
limit może wzrosnąć około `n` razy, a autoscaling zmienia go w czasie.

Timeout Redisa wynosi 500 ms, ale oczekiwanie tyle dla każdego requestu podczas
awarii wywołałoby przeciążenie wtórne. `RedisAvailabilityGate` po trzech błędach
otwiera się na 2 s, dzięki czemu kolejne requesty od razu stosują strategię
fallback. Jest to mały circuit breaker dydaktyczny; do produkcji potrzebne są
konfigurowalne progi, half-open probes, jitter i SLO zależności.

Failure strategy jest właściwością reguły, bo ryzyko biznesowe endpointów jest
różne. Płatność może wymagać fail-closed, odczyt katalogu fail-open. Jedna
globalna strategia zwykle jest zbyt uproszczona.

## 10. Multi-region

Najprostszy wariant to niezależny bucket w każdym regionie. Daje małą latencję i
odporność na podział sieci, ale globalny klient może zużyć sumę limitów regionów.
Przy `R` regionach lokalny limit `L` pozwala w skrajnym przypadku na `R × L`.

Możliwe modele:

- regionalny limit — szybki, przewidywalny lokalnie, globalnie luźny;
- dzielenie globalnego budżetu na regiony — prostsze, lecz nierówny ruch marnuje
  pojemność;
- leasing porcji tokenów — region rezerwuje budżet centralnie i wydaje lokalnie;
- globalny store — dokładniejszy, ale dodaje latency i zależność cross-region;
- CRDT/async replication — dostępne, ale limit jest przybliżony i trzeba policzyć
  maksymalny overshoot.

`GLOBAL_APPROXIMATE` w modelu klucza jest oznaczeniem intencji, a nie dowodem
globalnej spójności. Bez rzeczywistej globalnej topologii Redis pozostaje
production boundary.

## 11. Dynamiczna konfiguracja

Control plane nie powinien modyfikować struktur używanych równocześnie przez
requesty. `DynamicConfigService` publikuje niezmienny, posortowany snapshot.
Zmiana wymaga bieżącej wersji w `If-Match`; stary operator otrzymuje `412`, więc
nie nadpisze cudzego update'u. Każda mutacja tworzy wpis audit.

```http
POST /admin/rules
X-Admin-Token: ...
X-Admin-Actor: on-call@example
If-Match: 7
```

Domyślny Admin API pozostaje lokalną symulacją, żeby szybki profil nie wymagał
bazy. Profil `durable` wpina `JdbcControlPlaneStore` w ten sam Admin API, używa
blokady `FOR UPDATE`, optimistic version i okresowej synchronizacji
`RuleReplica`. Wyłączenie jest trwałym tombstone, a rollback publikuje dawną
regułę jako nową wersję — historia nigdy nie jest przepisywana. Replika odrzuca
lukę i podczas awarii zachowuje last-known-good. Metryka `config.lag` pokazuje
różnicę między PostgreSQL head i lokalnym snapshotem.

Regułę można najpierw uruchomić z `enforcementMode=MONITOR`. Bucket jest liczony,
a `SHADOW_WOULD_DENY` mierzony, lecz ruch nie jest blokowany. Dopiero jawna
zmiana na `ENFORCE` kończy canary polityki. Pełny system nadal potrzebuje:

1. trwałego, wersjonowanego źródła prawdy;
2. walidacji i dry-run przed publikacją;
3. podpisanego eventu lub watch streamu;
4. atomowej podmiany snapshotu w każdej instancji;
5. alarmu na config lag;
6. kryterium automatycznego rollbacku złej polityki.

## 12. Rate limit, quota i billing

Te trzy mechanizmy rozwiązują inne problemy:

- **rate limit** — krótkie okno; chroni system teraz;
- **quota** — długi budżet produktu, np. operacje w miesiącu;
- **billing ledger** — trwały, audytowalny zapis rozliczeniowy.

`QuotaService` przechowuje w Redisie atomowy `INCRBY` z datą wygaśnięcia po
zamknięciu miesiąca. Koszt to koszt operacji endpointowej, nie suma kosztów
wszystkich polityk ochronnych. Awaria quota po pozytywnej decyzji nie odwraca
requestu — licznik jest estymacją online.

W profilu domyślnym event usage jest best effort. Profil `durable` wybiera
`DurableUsageEventSink`: receipt i intencja publikacji trafiają razem do
PostgreSQL, a relay używa `SKIP LOCKED`, lease i reclaim. Publish-before-mark
może dać duplikat, więc `eventId` jest kluczem idempotencji konsumenta. Poison
event po dziesięciu próbach przechodzi do stanu `DEAD`, dzięki czemu nie blokuje
partycji pracy. `authoritativeUsage()` wspiera reconciliation, ale limiter nadal
nie jest autorytatywnym ledgerem biznesowym.

## 13. Obserwowalność

Najważniejsze metryki:

- `rate_limiter_decisions_total{decision}` — wynik końcowy;
- `rate_limiter_redis_decisions_total{rule.type,decision}` — wynik data plane;
- `rate_limiter_redis_latency_seconds` — histogram, analizuj p95/p99;
- `rate_limiter_redis_errors_total{rule.type}` — błędy zależności;
- `rate_limiter_fallback_total{strategy}` — wejścia w degradację;
- `rate_limiter_redis_circuit_open` — bieżący stan circuit breakera;
- `rate_limiter_config_version` — wersja lokalnego snapshotu;
- `rate_limiter_config_lag` — różnica względem trwałego head;
- `rate_limiter_usage_receipt_total` oraz `rate_limiter_usage_outbox_total` — trwałość, retry i DLQ;
- `rate_limiter_quota_errors_total` i wyniki publish usage.

Nie dodajemy `userId`, API key ani tenant ID jako tagów ogólnego licznika — ich
nieograniczona cardinality może przeciążyć system metryk. Identyfikatory są
przydatne w próbkowanych logach/audycie z odpowiednią retencją i ochroną PII.

Sam wzrost `denied` nie zawsze oznacza awarię: limiter może właśnie prawidłowo
powstrzymywać klienta. Alert powinien łączyć error rate, fallback, circuit state,
latency Redisa i saturation chronionego downstreamu. Procedura znajduje się w
[`observability/RUNBOOK.md`](observability/RUNBOOK.md).

## 14. Testowanie

Szybkie testy nie wymagają lokalnego Redisa ani Kafki:

```powershell
.\mvnw.cmd test
```

Dowodzą m.in.:

- różnic między pięcioma modelami algorytmów;
- kontrprzykładu check-then-act;
- kumulatywnej hierarchii i attempt accounting;
- semantyki exact, `/*`, `/**`;
- walidacji i optimistic concurrency konfiguracji;
- weighted cost oraz cleanup fallbacku;
- fail-open/fail-closed/local fallback;
- HMAC, trusted proxy i rotacji admin tokenu;
- tego, że awaria estymacji quota nie zmienia dozwolonej decyzji;
- ograniczonego ryzyka lease'ów regionalnych i fencing tokenu;
- utworzenia niskokardynalnej obserwacji decyzji dla OTel.

Test infrastrukturalny:

```powershell
.\mvnw.cmd -Pinfrastructure-tests verify
```

uruchamia Redis `7.4-alpine` oraz PostgreSQL `17-alpine`. Sprawdza atomowość pod
konkurencją, bucket TTL i odtworzenie, koszt ważony, quota TTL, tombstone i
rollback konfiguracji, konwergencję replik, reclaim oraz DLQ outboxa. Pojedynczą klasę wybiera `-Dit.test=NazwaTestu`. Profil jest
osobny, aby zwykłe `verify` pozostało szybkie, a brak Dockera nie zmieniał wyniku
testów jednostkowych.

Load test używa w k6 modelu **open workload** (`constant-arrival-rate`). Closed
model z ustaloną liczbą VU ukrywa coordinated omission: gdy system zwalnia,
generator sam wysyła mniej requestów i raport wygląda zbyt dobrze.

```powershell
$env:RATE = 500
$env:DURATION = "2m"
k6 run load-test.js
```

Przed eksperymentem określ expected RPS, p99, maksymalny odsetek błędów,
saturation i kryterium przerwania. Osobno wykonaj load, stress, spike i soak;
jeden krótki test nie odpowiada na wszystkie pytania.

## 15. Uruchomienie

Wymagania: Java 21, Docker dla infrastruktury.

```powershell
.\mvnw.cmd clean verify
docker compose up --build

# trwały control plane i usage outbox (aplikacja na porcie 8081)
docker compose --profile durable up --build rate-limiter-durable

# aplikacja korzystająca z prawdziwej topologii Redis Cluster (port 8082)
docker compose --profile cluster --profile cluster-app up --build rate-limiter-cluster
```

Przykładowe endpointy:

```text
GET  /api/users
POST /api/payments
POST /api/exports
GET  /admin/rules
GET  /admin/audit
GET  /actuator/prometheus
GET  /swagger-ui/index.html
```

Compose uruchamia pojedynczy Redis, Kafka, Collector OTLP, Prometheus i Grafanę. Obraz aplikacji
działa jako nieuprzywilejowany użytkownik. Dane i hasła compose są wyłącznie
lokalne; produkcja wymaga TLS, secret store, uwierzytelnienia Redis/Kafka,
network policies, limitów zasobów i pinowania obrazów po digest.

## 16. Failure matrix

| Awaria | Zachowanie laboratorium | Ryzyko produkcyjne |
| --- | --- | --- |
| Redis timeout | strategia per rule, potem circuit | overshoot albo niedostępność |
| Redis restart | stan bucketów może zniknąć | chwilowy burst po recovery |
| Kafka down | best effort może zgubić event; durable retry/DLQ | konsument nadal musi deduplikować |
| quota Redis down | accepted request pozostaje accepted | niedokładny podgląd usage |
| zły config update | walidacja, shadow, `If-Match`, rollback | automatyczne kryterium rollbacku zależy od SLO |
| relay pada po publish | lease wygasa i event wraca | duplikat; consumer deduplikuje `eventId` |
| podział regionów | regionalne buckety działają | globalny overshoot |
| spoofed identity headers | ignorowane od niezaufanego peer | błędna lista proxy psuje granicę |

## 17. Kiedy użyć innego rozwiązania

Limiter w aplikacji daje dostęp do tożsamości domenowej i kosztu operacji, ale
każdy request dociera już do procesu. Gateway/Envoy/API Management może odrzucić
ruch wcześniej i prościej chronić całą flotę. Biblioteka lokalna ma najniższą
latencję, lecz nie daje wspólnego limitu. Często potrzebne są warstwy:

```text
edge/WAF limit → gateway/API key limit → application/tenant-operation limit
```

Nie wolno bezrefleksyjnie dodać retry na każdej warstwie. Retry gatewaya,
klienta i service mesh może zwielokrotnić ruch oraz zużycie tokenów. Kontrakt
powinien definiować właściciela retry i to, czy każda próba jest osobno liczona.

## 18. Trwały control plane

Control plane ma inne SLO niż data plane. Publikacja może być wolniejsza i
silnie spójna; odczyt na ścieżce requestu musi korzystać z lokalnego snapshotu.
`JdbcControlPlaneStore` przechowuje append-only log zmian:

```text
operator --expectedVersion--> PostgreSQL head FOR UPDATE
         --append version N--> rule_change
data-plane replica --changesAfter(localVersion)--> atomic snapshot swap
```

Blokada serializuje dwóch publisherów, a `expectedVersion` wykrywa lost update.
Replika wymaga ciągłych wersji; luka oznacza błąd synchronizacji, nie zgodę na
częściową konfigurację. Dwie instancje mogą przez krótki czas mieć różne wersje,
dlatego rollout musi mierzyć config lag i określić maksymalny czas konwergencji.

Snapshot powinien zachować last-known-good podczas awarii control plane. Nie
należy odpytywać PostgreSQL dla każdego requestu. Event notification przyspiesza
propagację, ale okresowy poll nadal jest potrzebny jako mechanizm naprawczy.

## 19. Outbox i reconciliation usage

Bez outboxu istnieje okno:

```text
decyzja/operacja zakończona → crash → Kafka send nigdy nie nastąpił
```

W profilu `durable` `JdbcUsageOutbox.record()` zapisuje receipt i intencję publikacji w jednej
transakcji. Relay rezerwuje batch przez `FOR UPDATE SKIP LOCKED` oraz lease.
Crash przed publish pozostawia pracę do odzyskania. Crash po publish, lecz przed
`markPublished`, powoduje redelivery — dlatego downstream deduplikuje `eventId`.
Retry jest ograniczony; poison event otrzymuje stan `DEAD` i wymaga operacyjnej
decyzji o naprawie lub kontrolowanym replayu.

Exactly-once end-to-end nie wynika z transakcji w jednej bazie. Praktyczny model
to at-least-once transport + idempotentny konsument + okresowa reconciliation.
`authoritativeUsage()` pozwala porównać projekcję z receiptami. Ostateczny billing
powinien jednak uzgadniać wynik z systemem, który wykonał efekt biznesowy.

## 20. Prawdziwy Redis Cluster

Profil compose `cluster` tworzy sześć węzłów: trzy masters i trzy replicas, a
`redis-cluster-init` przypisuje wszystkie 16 384 sloty. Drill wykonuje migrację
slotu oraz kontrolowany failover repliki:

```powershell
docker compose --profile cluster --profile cluster-drill up `
  --abort-on-container-exit redis-cluster-drill
```

Resharding przechodzi przez stany `MIGRATING/IMPORTING`; klient musi obsługiwać
`MOVED` i `ASK` oraz odświeżać topologię. Replikacja Redis jest asynchroniczna,
więc potwierdzony zapis może zginąć przy awarii primary przed replikacją. Opcje
`WAIT`, persistence i liczba replik zmniejszają ryzyko, ale nie tworzą
linearizowalnej bazy bez kosztu.

Profil Spring `cluster` konfiguruje Lettuce z adaptive i okresowym topology
refresh. Usługa `rate-limiter-cluster` działa wewnątrz sieci Compose, więc
adresy ogłaszane przez węzły są osiągalne. Drill zapisuje klucz przez klienta
redirect-aware, wykonuje reshard i failover, a następnie ponownie odczytuje dane.

Skrypt Lua może używać wielu kluczy tylko w jednym hash slocie. Hash tag pomaga
współlokować dane principalu, lecz reguła globalna i userowa celowo mają różne
hotspoty. Failover powinien być testowany razem z timeoutem klienta, topology
refresh, pulą połączeń i strategią fallback, a nie tylko komendą `PING`.

## 21. Globalny budżet przez lease

`GlobalTokenLeaseLab` pokazuje centralny allocator rozdający regionom porcje
tokenów. Region obsługuje requesty lokalnie aż do wyczerpania lease:

```text
global budget → lease EU (40) + lease US (40) + unleased (20)
```

Podczas partition region nie może dostać nowej porcji, ale może wydać pozostałą.
Maksymalne chwilowe ryzyko to suma niewydanych tokenów we wszystkich aktywnych
lease'ach. Mniejszy batch zmniejsza overshoot, lecz zwiększa ruch do allocatora.

Wygaśnięcie zwraca tylko niewykorzystaną część. Fencing token chroni przed
opóźnionym właścicielem po regrant: sam TTL/blokada nie wystarcza, bo stary
proces może wznowić pracę po pauzie GC lub partition. Clock skew wymaga czasu
allocatora, marginesu bezpieczeństwa albo odnawiania opartego o lease protocol.

## 22. Tracing end-to-end

Spring MVC odbiera i propaguje W3C `traceparent`. `RateLimiterEngine` tworzy
obserwację `rate-limiter.decision` z niskokardynalnymi polami `http.method` i
`decision`. Micrometer Tracing łączy ją z aktywnym spanem HTTP, a bridge OTel
wysyła trace przez OTLP do Collectora.

Nie wolno dodawać pełnego API key, user ID ani ścieżki z dynamicznym ID jako
atrybutu każdego spanu. Trace ma odpowiedzieć, czy czas zużył matcher, Redis,
fallback czy downstream. `observability/otel-collector.yml` używa debug exportera
do laboratorium; produkcja wymaga backendu trace, TLS, auth, batchingu i sampling.

Head sampling podejmuje decyzję przed poznaniem wyniku, więc może zgubić rzadkie
błędy. Tail sampling w Collectorze może zachować `429`, wyjątki i wolne trace,
ale buforuje całe ślady i wymaga spójnego routingu spanów danego trace.

## 23. Chaos i soak

`chaos/redis-outage-drill.ps1` zatrzymuje Redis, generuje ruch i zawsze próbuje
przywrócić zależność. Test należy oceniać przez kontrakt reguły:

- FAIL_OPEN — request działa, ale ochrona jest osłabiona;
- FAIL_CLOSED — oczekiwane jest odrzucenie;
- LOCAL_FALLBACK — limit staje się per instancja;
- circuit powinien ograniczyć liczbę wolnych prób do Redisa.

`k6/soak-test.js` utrzymuje open workload domyślnie przez 30 minut. Obserwuj p99,
dropped iterations, heap/GC, liczbę kluczy i pamięć Redisa, reconnects, fallback
oraz stabilność cardinality metryk. Restart store w połowie testu powinien
pokazać kontrolowaną degradację i powrót bez retry storm.

Game day musi mieć ownera, blast radius, warunek przerwania, dashboard, rollback
i hipotezę. Chaos bez oczekiwanego niezmiennika jest tylko awarią wywołaną przez
zespół.

## 24. Granice gotowości produkcyjnej

Projekt dowodzi algorytmu i najważniejszych decyzji, ale świadomie nie obiecuje:

- ścisłego limitu globalnego między regionami;
- atomowej transakcji wielu bucketów w różnych slotach;
- pełnego IAM/mTLS dla Admin API;
- fairness między tenantami podczas globalnego przeciążenia;
- zweryfikowanej konfiguracji konkretnego managed Redis i backendu tracingowego;
- wspólnej transakcji z efektem biznesowym wykonywanym w innym serwisie.

To są następne decyzje zależne od SLO, kosztu, topologii i modelu zagrożeń — nie
braki, które można wiarygodnie ukryć kilkoma dodatkowymi adnotacjami Springa.

## 25. Fairness, overload i concurrency limiting

Rate limit kontroluje liczbę przyjęć w czasie, lecz nie liczbę operacji
wykonywanych jednocześnie. Jeśli request trwa 10 s, nawet umiarkowane 100 RPS
może oznaczać około 1000 prac w toku. Zależność przybliża Little's Law:

```text
concurrency ≈ throughput × średni czas przebywania w systemie
```

Dlatego limiter nie zastępuje bulkheadu, bounded queue ani adaptive concurrency
limit. Przed wolnym downstreamem potrzebny jest limit prac w toku oraz load
shedding. W przeciwnym razie request może przejść Token Bucket i długo czekać w
nieograniczonej kolejce, zużywając pamięć oraz deadline klienta.

Hierarchia tenantów ogranicza noisy neighbor, ale nie gwarantuje fairness. Można
użyć osobnych pul, weighted fair queueing albo rezerwacji minimalnego udziału.
Ścisła równość bywa niewłaściwa: tenant płatności może mieć wyższy priorytet niż
batch eksportowy. Polityka powinna wynikać z klasy ruchu i SLO, a nie z kolejności
wątków w JVM.

`AdaptiveConcurrencyLimit` jest wykonywalnym modelem AIMD: sukces zwiększa
limit powoli, sygnał przeciążenia zmniejsza go multiplikatywnie. Pokazuje, że
ten sam ruch może przejść limit czasowy, a mimo to zostać odrzucony z powodu
wyczerpania bezpiecznej liczby prac w toku.

## 26. Wybór miejsca i storage

| Miejsce egzekwowania | Zaleta | Ograniczenie |
| --- | --- | --- |
| klient | mniej zbędnego ruchu | niezaufany, nie chroni przed atakiem |
| CDN/WAF | odrzuca najwcześniej | mało kontekstu domenowego |
| API gateway | wspólne klucze i polityki | potencjalny bottleneck/control plane |
| sidecar/service mesh | spójna polityka techniczna | retry na wielu warstwach |
| aplikacja | zna tenant, operację i koszt | request dotarł już do procesu |

Redis jest dobrym wyborem dla współdzielonego, krótkowiecznego stanu i atomowych
skryptów, ale nie jest jedyną opcją:

- pamięć procesu — najniższa latency, limit per instancja;
- gateway-managed limiter — mniej własnego kodu, zależność od platformy;
- relacyjna baza — trwałość i transakcje, zwykle za droga na każdy request;
- dedykowany service — centralne zasady, dodatkowy hop i osobne SLO;
- aproksymacyjne lokalne liczniki — duża skala kosztem kontrolowanego overshoot.

Wybór opisuje trójkąt: dokładność, dostępność i latency. Podczas partition nie da
się jednocześnie zachować ścisłego globalnego limitu i niezależnie przyjmować
ruchu we wszystkich regionach. System musi jawnie wybrać, które ryzyko jest
akceptowalne.

## 27. Checklist projektu systemowego

Przed zaakceptowaniem projektu odpowiedz konkretnie:

1. Jaki zasób chronimy i czy liczymy próby, sukcesy czy koszt?
2. Jaki burst, sustained rate i maksymalny overshoot są dozwolone?
3. Co jest principalem i kto poświadcza jego tożsamość?
4. Czy polityki się sumują, nadpisują, czy wybieramy najbardziej restrykcyjną?
5. Gdzie znajduje się punkt atomowości i źródło czasu?
6. Jak zachowujemy się przy timeout, restart, failover i partition?
7. Czy limit jest regionalny, globalny dokładny czy globalny przybliżony?
8. Jak konfiguracja jest walidowana, wersjonowana, propagowana i wycofywana?
9. Jak klient poznaje limit, reset i semantykę retry?
10. Jak odróżniamy rate limit, quota, usage receipt i billing ledger?
11. Które metryki, trace i alerty dowodzą poprawności bez wysokiej cardinality?
12. Jak testujemy race, clock skew, hot key, reshard, failover, soak i recovery?

Jeżeli odpowiedzi są mierzalne i powiązane z testem albo świadomie nazwaną
granicą, projekt jest znacznie bliższy wiarygodnemu system designowi niż diagram
z samym napisem „Redis Cluster”.
