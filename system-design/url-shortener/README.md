# URL Shortener — wykonywalne studium System Design

## Zacznij tutaj

| Element | Odpowiedź |
|---|---|
| Najważniejszy niezmiennik | Jeden `shortCode` wskazuje najwyżej jeden URL, a rekord `BLOCKED`, `DELETED` lub wygasły nigdy nie może zostać przekierowany. |
| Krytyczny przepływ | `GET /{shortCode}` → wersjonowana projekcja Redis → fallback do primary PostgreSQL → `302` → best-effort event kliknięcia. |
| Najgroźniejszy błąd | Stary wpis cache lub opóźniony event `ACTIVE` może wskrzesić zablokowany link. |
| Rekomendowany szybki test | `ShortUrlApiControllerTest`, `ShortUrlTest`, `VersionedUrlLookupStoreTest`. |
| Rekomendowany test infrastruktury | `CacheRevocationInfrastructureTest`, `PostgreSqlUrlInvariantInfrastructureTest`, `RedisCounterInfrastructureTest`, `RabbitAnalyticsInfrastructureTest`. |
| Świadome ograniczenie | Globalny magazyn jest portem produkcyjnym i lokalnym adapterem JPA; prawdziwa replikacja geograficzna jest reprezentowana przez deterministyczną symulację wersji. |

Projekt nie jest zbiorem endpointów CRUD. Pokazuje konsekwencje asymetrii ruchu
URL shortenera: tworzenie jest rzadkie, redirect bardzo częsty, a blokada musi
rozchodzić się szybciej niż zwykła aktualizacja danych.

## Problem i wymagania

URL shortener zamienia długi adres na krótki identyfikator i później odtwarza
oryginalny cel. Samo mapowanie jest proste; interesujący problem projektowy
zaczyna się wtedy, gdy odczytów jest o kilka rzędów wielkości więcej niż zapisów,
popularny link staje się hot key, a usunięcie lub blokada musi szybko unieważnić
kopie rozproszone po cache'ach.

### Wymagania funkcjonalne

- utworzenie krótkiego linku generowanego przez system lub z własnym aliasem;
- opcjonalny termin ważności;
- przekierowanie wyłącznie dla aktywnego, niewygasłego rekordu;
- blokowanie i ponowne aktywowanie przez administrację;
- bezpieczne ponowienie komendy create przez `Idempotency-Key`;
- rejestrowanie kliknięć bez pogarszania dostępności redirectu;
- lookup wykonywany także z warstwy edge.

### Wymagania niefunkcjonalne

- jednoznaczność: kod nie może wskazywać dwóch celów;
- bardzo niski p95/p99 redirectu i wysoka dostępność ścieżki odczytu;
- read-your-writes po utworzeniu linku;
- ograniczone okno widoczności zablokowanego linku w projekcjach;
- odporność na retry, duplikaty eventów i zmianę kolejności aktualizacji;
- ograniczenie abuse bez uzależniania każdego redirectu od sprawnego Redisa;
- brak sekretów, pełnych URL-i i surowych adresów IP w telemetryce.

### Świadomy out of scope

Interfejs użytkownika, rozliczenia, prywatne linki i integracja z konkretnym
globalnym datastore nie są częścią wykonywalnego rdzenia. Projekt pokazuje
własność linku w granicy enterprise API key, lecz nie modeluje pełnego cyklu
życia konta. README opisuje miejsca podłączenia pozostałych elementów, ale nie
przypisuje atrapom gwarancji produkcyjnych.

## Model mentalny architektury

```text
                              ┌─────────────────────┐
POST create ─────────────────▶│ PostgreSQL          │◀──── admin moderation
                              │ source of truth     │
                              │ URL + outbox        │
                              └─────────┬───────────┘
                                        │ after commit / retry
                                        ▼
edge/CDN ─▶ redirect API ─────────────▶ Redis lookup projection
                    │ cache miss          │
                    └─────────────────────┘
                    │
                    └── best-effort click ─▶ RabbitMQ ─▶ SQL analytics
```

Architektura rozdziela dane kanoniczne od projekcji. PostgreSQL rozstrzyga
poprawność zapisu, Redis skraca ścieżkę odczytu, a RabbitMQ izoluje analitykę od
redirectu. Każda dodatkowa kopia poprawia latency lub skalowanie, lecz tworzy
nowy problem spójności i odtwarzania. Dlatego projekt nie mówi po prostu
„użyj cache”; określa również wersję rekordu, moment publikacji i zachowanie po
awarii aktualizacji.

## Zakres implementacji

| Obszar | Stan | Gwarancja lub granica |
|---|---|---|
| PostgreSQL + Flyway | `correct` | źródło prawdy, unikalny `short_code`, wersja optimistic locking |
| ID → Base62 | `correct` | brak kolizji przy jednej sekwencji PostgreSQL; luki w sekwencji są dozwolone |
| Custom alias | `correct` | normalizacja i constraint rozstrzygający równoległy wyścig |
| `Idempotency-Key` | `correct` | namespace aktora i operacji, fingerprint oraz odpowiedź zapisane atomowo z efektem |
| Redis lookup | `correct` w granicy jednej instancji Redis | Lua odrzuca starszą wersję; status/expiry tworzą bounded negative cache |
| Revocation | `correct` + mierzalne SLO | wersjonowany tombstone, outbox z lease/backoff/DLQ i port purge CDN |
| Fixed-window rate limit | `correct` | Lua wykonuje atomowo `INCR + PEXPIRE`; świadomy tryb fail-open |
| Analytics | `correct` dla efektu SQL | atomowy marker `event_id` i upsert agregatu eliminują duplikat oraz lost update |
| Publikacja analytics | dwa jawne tryby | durable outbox ma claim/lease, backoff i publisher confirm przed `published_at` |
| RabbitMQ retry/DLQ | `production practice` | at-least-once, retry listenera i DLQ sprawdzane z brokerem |
| Edge worker | `production boundary` | kontrakt cache jest testowany lokalnie; purge konkretnego CDN wymaga adaptera dostawcy |
| Ownership i lifecycle | `correct` w granicy API key | owner z uwierzytelnionego principal, `If-Match`, soft delete i audit log |
| Rotacja sekretów | `production practice` | aktywny i poprzedni token/HMAC są akceptowane w kontrolowanym oknie |
| Multi-region | `simulation` | single-writer versioning oraz model konfliktu active-active bez wall clock |
| Read replica | `production boundary` | redirect po cache miss czyta primary, aby nie zwrócić fałszywego 404 |

## Mapa najważniejszego kodu

- `ShortUrlService` — cienka fasada stabilizująca API warstwy aplikacyjnej.
- `CreateShortUrlUseCase`, `ResolveShortUrlUseCase`, `ModerateShortUrlUseCase`
  oraz `ShortUrlQuery` — osobne granice transakcji i odpowiedzialności.
- `ShortUrlCacheService` — wersjonowana projekcja lookup w Redisie.
- `UrlProjectionOutboxService` — zapisuje intencję projekcji w transakcji URL-a.
- `UrlProjectionUpdater` — szybka dostawa `AFTER_COMMIT`.
- `UrlProjectionOutboxRelay` — ponawia niedostarczone projekcje at-least-once.
- `UrlProjectionOutboxClaims` — lease i `SKIP LOCKED` dla wielu instancji relaya.
- `IdempotentCreateService` — waliduje klucz, fingerprint i odtwarza odpowiedź.
- `IdempotentBulkCreateService` — izoluje ten sam klucz pomiędzy tenantami.
- `RateLimitService` — atomowy fixed window w Redisie.
- `ClickEventPublisher` i `AnalyticsService` — best-effort publikacja oraz trwała,
  idempotentna konsumpcja.
- `VersionedUrlLookupStore` — deterministyczna symulacja reorderu multi-region.
- `edge/cloudflare/worker.js` — cienka warstwa cache i redirectu na edge.
- `EdgeCacheInvalidator` — port purge; lokalny adapter jawnie nie udaje dostawcy CDN.
- `DurableClickPublisher` i `ClickPublishOutboxRelay` — opcjonalna trwała ścieżka analytics.
- `ClickPublishOutboxClaims` — rozłączne batch claims i odzyskiwalny lease relaya.
- `ClickAnalyticsWriter` — atomowy marker eventu i bezpieczna inkrementacja agregatu.
- `ProjectionReconciliationService` — dry-run i kontrolowana naprawa projekcji Redis.
- `TrustedClientAddressResolver` — bezpieczna interpretacja `X-Forwarded-For`.

## Niezmienniki

1. `short_code` jest globalnie unikalny w obrębie źródłowej bazy.
2. To constraint, a nie wcześniejsze `exists`, rozstrzyga concurrent custom alias.
3. W chwili `expiresAt` link jest już nieważny; jest to granica wyłączna.
4. `BLOCKED`, `DISABLED`, `DELETED` i `EXPIRED` zwracają `410`, nie redirect.
5. Projekcja nie może stać się widoczna przed commitem źródła.
6. Starsza wersja `ACTIVE` nie może nadpisać nowszego `BLOCKED`/`DELETED`.
7. Ten sam event kliknięcia może zmienić trwałe agregaty tylko raz.
8. Ten sam aktor, operacja, `Idempotency-Key` i request zwracają tę samą odpowiedź;
   inny request w tym samym namespace zwraca `409`, a tenanty nie współdzielą kluczy.

## Create flow

```text
POST /api/v1/urls
  → Bean Validation
  → walidacja publicznego HTTP(S) URL
  → rezerwacja Idempotency-Key (opcjonalnie)
  → nextval(url_id_seq) → Base62 albo custom alias
  → INSERT urls + INSERT url_projection_outbox
  → COMMIT
  → AFTER_COMMIT: Redis/global lookup
  → relay ponawia niedostarczony outbox
```

Sekwencja PostgreSQL jest kanonicznym mechanizmem obecnej implementacji. Nie
udajemy, że jest to Snowflake. Przy wielu niezależnych regionach zapisu należałoby
zastosować globalnie unikalny identyfikator, zakresy ID albo ownership shardów.

Sekwencyjny Base62 jest możliwy do enumeracji. Krótki kod nie jest kontrolą
dostępu. Prywatny link wymaga autoryzacji albo losowego sekretu o odpowiedniej
entropii.

## Teoria generowania krótkich kodów

Base62 używa cyfr oraz małych i wielkich liter, więc kod długości `k` daje
`62^k` możliwych wartości. Dla siedmiu znaków jest to ponad 3,5 biliona kodów.
Sama wielkość przestrzeni nie mówi jednak nic o kolizjach — zależą one od
algorytmu przydziału.

W projekcie liczbowy identyfikator z sekwencji PostgreSQL jest kodowany do Base62:

```text
database sequence → unikalne ID → zapis pozycyjny Base62 → shortCode
```

Zaletami są brak losowych kolizji, krótki kod i prosty retry. Wadami są możliwość
enumeracji, zależność ścieżki zapisu od generatora ID oraz brak globalnej
unikalności przy kilku niezależnych bazach. Alternatywy mają inne koszty:

| Strategia | Zaleta | Koszt |
|---|---|---|
| losowy kod + unique constraint | brak centralnej sekwencji, trudniejsza enumeracja | prawdopodobieństwo kolizji i retry rośnie wraz z zajęciem przestrzeni |
| UUID/ULID | łatwe generowanie rozproszone | dłuższy kod; UUID pogarsza lokalność indeksu |
| Snowflake-like ID | globalna unikalność i przybliżony porządek czasu | zależność od worker ID, zegara i procedur na clock rollback |
| przydzielane zakresy ID | szybkie lokalne generowanie | niewykorzystane zakresy i potrzeba koordynatora |

Luki w sekwencji po rollbacku są prawidłowe. ID jest identyfikatorem, a nie
licznikiem biznesowym. Próba „odzyskiwania” numerów zwiększałaby koordynację bez
poprawy wymaganej gwarancji.

## Redirect i revocation flow

```text
GET /abc
  → Redis url:abc
      hit: sprawdź status + expiresAt
      miss/error: primary PostgreSQL
  → 302 Location
  → publikacja click.created (best effort)
```

Cache jest derived state. Wartość zawiera `status`, `expiresAt` i `version`, nie
tylko docelowy URL. Blokada zapisuje wersjonowany rekord do outboxa. Dopiero po
commicie atomowy skrypt Lua nadpisuje cache tombstonem `BLOCKED`. Skrypt porównuje
wersję już zapisaną w Redisie, więc opóźniony `ACTIVE v7` nie może nadpisać
`BLOCKED v8`. Odczyt tombstone zwraca `410`, ale nie usuwa go: bounded negative
cache chroni zarówno bazę przed powtarzanymi missami, jak i link przed replayem.

Nie istnieje darmowa gwarancja natychmiastowej invalidacji wszystkich cache'y.
Przyjęte wymaganie operacyjne to:

- Redis/origin projection: p99 poniżej 5 sekund;
- CDN/edge: TTL nie większy niż zaakceptowany revocation SLO albo purge dostawcy;
- alert, gdy najstarszy rekord outbox przekracza SLO;
- dla linków niezweryfikowanych lub wysokiego ryzyka: `Cache-Control: no-store`.

Publiczny redirect nie używa permanentnego `301`, ponieważ system zachowuje
kontrolę nad blokowaniem i wygaszaniem celu.

### Dlaczego `302`, a nie `301`?

Przeglądarki i pośrednie cache mogą pamiętać permanentny redirect długo poza
kontrolą originu. `302` pozostawia serwisowi możliwość zmiany polityki, blokady i
wygaśnięcia. `307` dodatkowo zachowuje metodę i body, co nie jest potrzebne dla
zwykłego `GET`. W systemie, w którym cel nigdy nie może się zmienić ani zostać
cofnięty, `301` może dać lepszy cache hit ratio, ale jest to decyzja produktu, nie
automatyczna optymalizacja.

## API

| Metoda | Ścieżka | Znaczenie |
|---|---|---|
| `POST` | `/api/v1/urls` | tworzenie; opcjonalny `Idempotency-Key` |
| `GET` | `/api/v1/urls/{code}` | metadane rekordu |
| `GET` | `/{code}` | `302`, `404` albo `410` |
| `POST` | `/api/v1/admin/urls/{code}/block` | wersjonowana moderacja |
| `POST` | `/api/v1/admin/urls/{code}/unblock` | przywrócenie, jeśli link nie wygasł |
| `DELETE` | `/api/v1/admin/urls/{code}` | soft delete z audytem i propagacją tombstone |
| `PATCH` | `/api/v1/enterprise/urls/{code}` | owner-only zmiana destination, opcjonalne `If-Match` |
| `DELETE` | `/api/v1/enterprise/urls/{code}` | owner-only soft delete |
| `POST` | `/api/v1/enterprise/urls/bulk` | tenantowy bulk create; opcjonalny `Idempotency-Key` |
| `POST` | `/api/v1/admin/urls/projections/reconcile` | dry-run lub naprawa Redis względem primary |
| `GET` | `/api/v1/dashboard/urls/{code}/analytics` | read model statystyk |
| `GET` | `/internal/edge/urls/{code}` | lookup chroniony tokenem edge |

Publiczny create tworzy link anonimowy. Endpoint enterprise ustala `ownerId` z
uwierzytelnionego API key — nigdy z pola body — i tylko ten principal może zmienić
cel lub usunąć rekord. `If-Match` przenosi optimistic locking do kontraktu HTTP:
stara wersja zwraca `412 Precondition Failed`, zamiast nadpisać nowszą decyzję.
Administracja pozostaje nadrzędną granicą moderacji i zapisuje actor/action/reason
w tabeli audytowej.

Prywatne URL-e nie są obietnicą tej wersji. Wymagałyby sprawdzania uprawnienia na
każdym redirectcie, co zmienia cache key, CDN policy, latency oraz model udostępniania.

### Semantyka API i kompatybilność

- `POST` nie jest z natury idempotentny; bezpieczeństwo retry zapewnia klucz komendy;
- `PATCH` zmienia część zasobu i wymaga wersji, gdy klient chce ochrony przed lost update;
- `DELETE` jest idempotentne semantycznie: stan końcowy pozostaje `DELETED`;
- `404` oznacza nieznany kod, natomiast `410` znany, lecz niedostępny link;
- `409` oznacza konflikt aliasu albo ponowne użycie klucza dla innej intencji;
- `412` mówi, że precondition `If-Match` nie odpowiada już wersji źródła.

Ewolucja publicznego kontraktu powinna być addytywna: nowe pola odpowiedzi muszą
być opcjonalne dla starych klientów, a znaczenia statusów nie wolno zmieniać bez
wersjonowania. Kod URL-a jest częścią publicznego identyfikatora i nie może zostać
ponownie przypisany po soft delete.

## Model danych i access patterns

### `urls`

Najważniejszy access pattern to lookup po `short_code`. Unikalny indeks służy
jednocześnie poprawności i wydajności. `version` chroni równoległą moderację.

Model danych zaczyna się od zapytań, nie od encji JPA:

| Access pattern | Mechanizm |
|---|---|
| znajdź cel po kodzie | unikalny indeks `urls(short_code)` |
| utwórz kod bez kolizji | sekwencja + constraint |
| rozstrzygnij równoległą moderację | optimistic locking przez `version` |
| znajdź statystyki kodu i dnia | indeks/projekcja analytics |
| ponów niedostarczoną projekcję | indeks po `sent_at` w outboxie |

Constraint jest ostatecznym arbitrem, ponieważ schemat `exists → insert` zawiera
okno wyścigu. Dwa procesy mogą jednocześnie zobaczyć brak aliasu. Dopiero atomowy
constraint bazy pozwala jednemu wygrać, a drugiemu zwrócić kontrolowany konflikt.

### `idempotency_records`

Primary key jest hashem namespace `(aktor, operacja, surowy klucz)`, dzięki czemu
ten sam tekst klucza dwóch tenantów nie powoduje konfliktu ani replayu cudzej
odpowiedzi. Rekord zawiera SHA-256 fingerprint kanonicznego requestu,
zserializowaną odpowiedź i expiry. Marker oraz efekt biznesowy commitują się razem. Awaria
przed commitem nie pozostawia fałszywego sukcesu.

Idempotencja nie oznacza „ignoruj każde kolejne żądanie”. Klucz identyfikuje jedną
intencję klienta, a fingerprint chroni przed przypadkowym użyciem go dla innej
komendy. Możliwe wyniki to:

| Stan | Odpowiedź |
|---|---|
| brak klucza | wykonaj komendę i atomowo zapisz wynik |
| ten sam klucz i fingerprint | odtwórz pierwotną odpowiedź |
| ten sam klucz, inny fingerprint | `409 Conflict` |
| klucz wygasł | nowa komenda według ustalonej polityki retencji |

Marker bez atomowego efektu biznesowego tworzyłby dwa niebezpieczne okna: zapisany
marker bez URL-a albo URL bez odpowiedzi możliwej do odtworzenia. Dlatego oba
rekordy należą do tej samej transakcji.

### `url_projection_outbox`

Outbox przechowuje wersjonowany payload. Dostawa jest at-least-once, dlatego
odbiorca musi wykonywać idempotentny upsert i ignorować starsze wersje.

Publikacja bezpośrednio do Redisa w środku transakcji bazy tworzyłaby dual write:
Redis może przyjąć zmianę, po czym PostgreSQL wykona rollback. Publikacja wyłącznie
po commicie usuwa ten przypadek, ale sama może się nie udać. Outbox zamienia więc
problem „czy oba zapisy się udały?” na prostszy „czy niedostarczony rekord zostanie
ponowiony?”. Nie daje exactly-once delivery; daje trwałą intencję i at-least-once.

Relay jest bezpieczny dla wielu instancji. Krótka transakcja wybiera porcję przez
`FOR UPDATE SKIP LOCKED`, zwiększa `attempts` i zakłada lease. Po awarii workera
`locked_until` wygasa, więc inna instancja może odzyskać rekord. Nieudana dostawa
otrzymuje wykładniczy backoff, a po limicie prób albo błędzie deserializacji trafia
do stanu dead-letter. Poison record nie blokuje kolejnych URL-i; operator naprawia
go według runbooka i odbudowuje projekcję ze źródła prawdy.

Kolejność globalna nie jest wymagana. Dwie aktualizacje tego samego kodu mogą
dotrzeć odwrotnie, dlatego odbiorca porównuje wersję. Zamiast uzyskiwać kosztowny
total order dla całego systemu, chronimy niezmiennik lokalnym ordering key i
idempotentną regułą aplikowania.

### Analytics

`click_events.event_id` jest markerem deduplikacji. `INSERT ... ON CONFLICT DO
NOTHING` atomowo rozstrzyga równoległe redelivery, a agregat dzienny używa
atomowego upsertu zamiast podatnego na lost update `read → increment → save`.
Obie operacje należą do jednej transakcji SQL. Redis counters są projekcją wykonywaną po
commicie. Brak FK do `urls` jest świadomy: dane analityczne mogą podlegać innej
retencji niż rekord operacyjny, ale usuwanie danych użytkownika musi uwzględnić
obie tabele.

Surowe eventy mają ograniczoną retencję, natomiast agregaty dzienne mogą żyć
dłużej. Cleanup usuwa rekordy po `clicked_at`, nie odtwarza efektów biznesowych i
nie usuwa agregatów. Przy dużej skali `click_events` powinno być partycjonowane po
czasie, aby retencja polegała na szybkim odłączeniu partycji zamiast masowego
`DELETE` generującego WAL i vacuum.

## Source of truth, cache i modele spójności

Source of truth to miejsce, które rozstrzyga konflikt i z którego można odbudować
pozostałe kopie. Tutaj jest nim PostgreSQL. Redis i edge są derived state — ich
utrata może pogorszyć wydajność, ale nie może zmienić znaczenia linku.

Ścieżka używa odmiany cache-aside:

1. odczytaj projekcję z Redisa;
2. przy miss lub awarii odczytaj rekord kanoniczny;
3. sprawdź status oraz expiry;
4. odbuduj cache z ograniczonym TTL.

Cache-aside dobrze skaluje odczyty, ale dopuszcza staleness. Sam TTL ogranicza
czas błędu, nie gwarantuje natychmiastowej spójności. Dla revocation potrzebne są
łącznie: wersjonowany tombstone, aktualizacja po commicie, retry z outboxa oraz
SLO określające maksymalne akceptowane okno. W systemie bezpieczeństwa o wymogu
zero-staleness redirect musiałby sprawdzać autorytatywne źródło albo używać
krótkotrwałego, kryptograficznie weryfikowalnego uprawnienia.

Read-your-writes oznacza, że klient po udanym create nie powinien dostać 404 dla
tego samego kodu. Read replica może jeszcze nie mieć nowego rekordu, dlatego cold
lookup celowo trafia do primary. To kosztuje część capacity zapisu, ale usuwa
anomalię widoczną dla użytkownika.

### Dlaczego relacyjna baza, a nie od razu key-value store?

Dominujący lookup `shortCode → destination` pasuje do rozproszonego magazynu
key-value, ale bieżący zakres potrzebuje również unikalnego aliasu, atomowego
idempotency record, optimistic locking, audytu i outboxa. PostgreSQL zapewnia te
reguły w jednej granicy transakcji i jest prostszym punktem startowym. Migracja do
key-value ma sens dopiero wtedy, gdy pomiary pokazują, że write throughput, rozmiar
indeksu lub wymagania multi-region przekraczają ten model.

W DynamoDB/Cassandrze trzeba zaprojektować dane od access patterns, jawnie wybrać
poziomy consistency i odtworzyć reguły: conditional put dla aliasu, osobny model
idempotencji, trwały change stream/outbox oraz rozwiązywanie konfliktów. Zmiana
silnika nie usuwa niezmienników; przenosi miejsce ich egzekwowania.

### Negative caching, stampede i awaria cache

Cache'owanie `404` może ograniczyć skanowanie losowych kodów, lecz tworzy konflikt
z read-your-writes: negatywny wpis sprzed create nie może zasłonić nowego URL-a.
Jeśli jest stosowany, musi mieć krótki TTL i zostać unieważniony wersjonowanym
zdarzeniem create. Bloom filter może odrzucać zdecydowanie nieistniejące kody
przed bazą, ale false positive nadal wymaga lookupu, a rebuild musi uwzględniać
nowe wpisy.

Po utracie Redisa wiele jednoczesnych missów może wywołać cache stampede.
Request coalescing/single-flight, jitter TTL, lokalny cache i ograniczenie liczby
concurrent fallbacków chronią bazę. Lock do przebudowy cache nie może stać się
warunkiem poprawności redirectu: po jego timeoutcie nadal istnieje source of truth.

## Rate limiting

Implementacja pokazuje fixed window. Skrypt Lua tworzy licznik i TTL atomowo, więc
awaria procesu nie pozostawi permanentnego klucza bez expiry.

Tryb fail-open jest właściwy dla dostępności publicznego redirectu, ale zwiększa
ryzyko nadużycia podczas awarii Redisa. Endpoint administracyjny lub kosztowny
bulk create może wymagać fail-closed. Fixed window pozwala też na burst na granicy
dwóch okien; sliding window albo token bucket są alternatywami dla ostrzejszego
wygładzania ruchu.

### Porównanie algorytmów

| Algorytm | Stan | Charakterystyka |
|---|---|---|
| fixed window | jeden licznik na okno | tani, lecz dopuszcza podwójny burst na granicy okien |
| sliding log | timestamp każdego requestu | dokładny, ale kosztowny pamięciowo |
| sliding window counter | kilka agregatów | kompromis dokładności i kosztu |
| token bucket | tokeny uzupełniane w czasie | pozwala na kontrolowany burst i ogranicza średnie tempo |
| leaky bucket | kolejka opróżniana stałym tempem | wygładza wyjście, ale może zwiększać latency |

Atomowość skryptu Lua jest istotna: osobne `INCR` oraz `EXPIRE` pozostawiają okno,
w którym proces może zginąć po zwiększeniu licznika. Powstały klucz bez TTL
blokowałby klienta bezterminowo.

## Analytics i delivery semantics

Redirect ma pierwszeństwo przed analityką. Publikacja RabbitMQ jest świadomie
best effort: błąd brokera jest mierzony, ale nie psuje `302`. Jeśli biznes wymaga
bezstratnych billingowych statystyk, click event musi trafić do durable logu/outboxa
przed odpowiedzią albo zostać zebrany z logów edge.

Projekt udostępnia oba warianty przez `app.queue.durable-publishing`:

| Tryb | Ścieżka redirectu | Gwarancja | Koszt |
|---|---|---|---|
| `false` | próba publikacji bez trwałego lokalnego zapisu | wysoka dostępność, możliwa utrata eventu | najmniejsze latency |
| `true` | zapis `click_publish_outbox`, później relay | trwała intencja producenta | redirect zależy od PostgreSQL i wykonuje write |

Sam successful `convertAndSend` nie jest jeszcze potwierdzeniem trwałości brokera.
Durable relay zakłada rozłączny lease, czeka na publisher confirm i dopiero po ACK
ustawia `published_at`. NACK, timeout lub błąd otrzymuje backoff, a po limicie prób
stan dead-letter. Awaria po ACK, lecz przed zapisem `published_at`, nadal może
spowodować duplikat — to właściwa granica at-least-once, obsługiwana przez consumer.

Consumer działa at-least-once:

- retry dotyczy błędów przejściowych;
- trwały poison record trafia do DLQ;
- `event_id` deduplikuje redelivery;
- atomowy `ON CONFLICT DO NOTHING` pozwala duplikatowi zakończyć się bez wyjątku
  i bez aktualizacji agregatu;
- upsert licznika dziennego nie traci równoległych, różnych kliknięć;
- Redis counter nie jest aktualizowany przed commitem SQL.

Gdy kolejka jest lokalnie wyłączona, analytics działa synchronicznie. Jest to
tryb demonstracyjny, a nie równoważna charakterystyka wydajności produkcji.

### At-most-once, at-least-once i exactly-once

- at-most-once może zgubić event, ale nie dostarczy go ponownie;
- at-least-once ponawia brak potwierdzenia, więc consumer musi tolerować duplikaty;
- exactly-once jest zawsze gwarancją w określonej granicy, np. transakcji brokera,
  a nie magiczną własnością całego przepływu przez broker, SQL, Redis i zewnętrzne API.

Ten projekt wybiera at-least-once dla konsumenta i osiąga exactly-once effect dla
trwałego agregatu SQL przez unikalny `event_id`. Redis pozostaje odbudowywalną
projekcją. Ważne rozróżnienie: deduplikacja zapisu nie oznacza, że dowolny efekt
zewnętrzny — e-mail, webhook czy charge — automatycznie wykona się tylko raz.

## Multi-region i spójność

Kanoniczny wariant to single-write-region:

- primary przyjmuje create i moderację;
- inne regiony konsumują wersjonowane projekcje;
- redirect może być lokalny, ale starsza wersja nie może wskrzesić linku;
- `DELETED` jest tombstonem, a nie fizycznym brakiem;
- cache miss po utworzeniu czyta primary, aby zachować read-your-writes.

`VersionedUrlLookupStore` kontroluje reorder deterministycznie. Nie jest adapterem
do DynamoDB, Cassandry ani Spannera. Active-active wymaga dodatkowo globalnych ID,
rozwiązywania konfliktów, ownership i semantyki zegara logicznego.

Z perspektywy CAP partycja sieciowa wymusza wybór zachowania, a nie jednej etykiety
dla całego systemu. Redirect może preferować dostępność i przez krótki czas użyć
starej projekcji. Moderacja bezpieczeństwa może preferować consistency i odrzucić
zapis w regionie bez kontaktu z właścicielem danych. Single writer upraszcza ten
konflikt: jeden region nadaje kolejne wersje, a pozostałe tylko je aplikują.

Timestamp zegara ściennego nie jest bezpieczną wersją konfliktu — clock skew może
sprawić, że starsza operacja wygląda na nowszą. Monotoniczna wersja ze źródła,
logiczny zegar lub fencing token opisują kolejność bez polegania na zgodności
zegarów maszyn.

## Skalowanie, partycjonowanie i hot keys

Warstwa HTTP jest bezstanowa, więc można ją skalować horyzontalnie za load
balancerem. Stan trafia do PostgreSQL, Redisa i brokera. Skalowanie instancji
aplikacji nie zwiększy capacity, jeśli wąskim gardłem pozostaje pool połączeń lub
pojedynczy shard danych.

Naturalnym kluczem partycjonowania lookupów jest stabilny hash `short_code`.
Range sharding skupiałby nowe, sekwencyjne kody w jednym zakresie. Consistent
hashing ogranicza liczbę przenoszonych kluczy podczas dodania noda, lecz wymaga
virtual nodes, rebalancingu i procedury obsługi utraty właściciela shardu.

Hot key jest innym problemem niż duża liczba kluczy. Viralowy link może obciążyć
jeden shard mimo równomiernego hash partitioningu. Pomagają wielopoziomowy cache,
request coalescing, lokalny cache procesu i CDN. Replikowanie hot value jest
bezpieczne tylko wtedy, gdy invalidacja nadal mieści się w revocation SLO.

Write path zwykle nie wymaga sharding na początku, bo stosunek read/write jest
bardzo wysoki. Gdy indeks `short_code` przestaje mieścić się w pamięci lub primary
nie spełnia SLO, można partycjonować rekordy po hash kodu. Generator ID, custom
alias uniqueness, ownership i routing zapisu muszą wtedy otrzymać globalną regułę;
sam podział tabeli nie rozwiązuje tych niezmienników.

Backpressure obowiązuje również w relayach. Batch, lease, limit prób i backoff
ograniczają ilość pracy. Nieskończone retry bez opóźnienia podczas awarii Redisa
tworzyłoby retry storm i konkurowało z ruchem użytkowników o połączenia z bazą.

## Failure modes

| Awaria | Zachowanie | Ryzyko / sygnał |
|---|---|---|
| Redis lookup | fallback do primary DB | wzrost latency i DB QPS, cache miss/error |
| Redis rate limit | fail-open | zwiększony abuse, licznik błędów limitera |
| PostgreSQL | brak tworzenia i cold redirectów | cache może obsłużyć część ruchu tylko do TTL |
| RabbitMQ publish | redirect działa, event może zginąć | `analytics.publish.failure` |
| Consumer | retry, następnie DLQ | lag, retry count, DLQ depth |
| Projection target | outbox pozostaje pending | oldest pending age i failure counter |
| Read replica lag | redirect czyta primary po cache miss | większy koszt primary w zamian za poprawność |
| CDN stale entry | możliwy redirect do czasu TTL/purge | revocation propagation SLO |

## Security i privacy

- Akceptowane są wyłącznie publiczne URL-e HTTP/HTTPS bez user info.
- Walidator sprawdza wszystkie odpowiedzi DNS, IPv4/IPv6, loopback, private,
  link-local i multicast.
- To nie rozwiązuje DNS rebindingu dla przyszłego server-side preview. Taki klient
  musi ponownie walidować każdy redirect DNS i przypiąć połączenie do sprawdzonego IP.
- Błąd DNS podczas tworzenia linku jest obecnie fail-open. To kompromis dostępności;
  crawler lub skaner wykonujący request serwerowy powinien działać fail-closed w
  izolowanym segmencie egress. Sam redirect przeglądarki nie jest SSRF-em backendu.
- Tokeny admin/edge są porównywane constant-time.
- API keys i adresy IP są pseudonimizowane przez HMAC-SHA256 z sekretem.
- `X-Forwarded-For` i `X-Real-IP` są honorowane tylko, gdy bezpośredni peer należy
  do `app.trusted-proxies.cidrs`; łańcuch jest analizowany od najbliższego hopu.
- Profil `prod` odmawia startu z pustymi lub przykładowymi sekretami.
- Rotacja tokenów admin/edge i enterprise HMAC akceptuje bieżący oraz poprzedni
  sekret. Poprzedni należy usunąć po czasie dłuższym niż maksymalny rollout.
- Nie logujemy pełnego URL-a, tokenów, kluczy API ani surowego IP.

Forwarded header jest wejściem niezaufanym. Branie pierwszego adresu bez sprawdzenia
bezpośredniego proxy pozwala klientowi ominąć rate limit i zatruć analytics. Lista
CIDR musi obejmować wyłącznie load balancery kontrolowane przez właściciela systemu;
publiczne zakresy całego dostawcy są zbyt szerokim trust boundary.

HMAC pseudonimizuje IP, ale nie anonimizuje danych. Stabilny marker nadal pozwala
łączyć zachowanie jednego użytkownika, więc podlega retencji i kontroli dostępu.
Rotacja sekretu przerywa korelację nowych i starych markerów; jeśli biznes wymaga
ciągłości, trzeba zapisać `key_version` lub w kontrolowanym procesie przeliczyć
markery przed wycofaniem poprzedniego klucza.

Samo przekierowanie przeglądarki nie jest SSRF-em wykonywanym przez backend.
Preview crawler, webhook lub skaner bezpieczeństwa tworzy nową trust boundary i
powinien mieć osobny egress policy.

## Observability

Własne metryki używają wyłącznie ograniczonych tagów:

- `url_shortener.redirect.lookup{source=cache|database}`;
- `url_shortener.projection.update{result=failure}`;
- `url_shortener.projection.propagation` jako histogram/timer;
- `url_shortener.analytics.publish{result=failure}`.
- `url_shortener.outbox.pending{kind=projection|click-publish}`;
- `url_shortener.outbox.dead_letters{kind=projection}`.

`shortCode`, URL, IP, API key ani user ID nie mogą być tagami — ich cardinality
jest nieograniczona. Do diagnozy konkretnego przypadku służą strukturalne logi z
request/event ID, z zachowaniem zasad privacy.

Przykładowe SLI/SLO, które należy ustalić przed produkcją:

- redirect availability i p95/p99 latency;
- cache hit ratio oraz DB fallback latency;
- p99 revocation propagation lag;
- wiek najstarszego pending outbox;
- consumer lag, retry rate i DLQ depth;
- odsetek zgubionych best-effort click events oszacowany z logów edge.

Publisher i consumer tworzą osobne observations, a Micrometer Tracing z mostem
OpenTelemetry i instrumentacja Spring AMQP propagują standardowy kontekst trace w
nagłówkach wiadomości. `requestId` podróżuje dodatkowo w `ClickMessage` jako
identyfikator biznesowy — korelacja po request ID nie zastępuje trace/span ID.
Eksporter OTLP i Collector pozostają zależnością wdrożeniową; bez nich trace jest
tworzony i propagowany, ale nie trafia do trwałego backendu telemetrycznego.

Reguły Prometheusa znajdują się w `observability/prometheus-alerts.yml`, a
procedury reakcji w `observability/RUNBOOK.md`. Alert powinien odpowiadać na SLO i
prowadzić do działania — sam dashboard bez ownera oraz runbooka nie skraca czasu
naprawy.

## Capacity — jawne założenia, nie wyniki

Przykład do dyskusji architektonicznej:

- 100 mln redirectów/dzień ≈ 1158 RPS średnio;
- peak factor 20 daje około 23 tys. RPS;
- 1 mln nowych URL-i/dzień ≈ 12 zapisów/s średnio;
- przy 500 bajtach rekordu 1 mld URL-i to około 500 GB danych bez indeksów i narzutu;
- cache working set należy policzyć z rozkładu popularności, a nie z całej tabeli.

Te liczby nie są benchmarkiem projektu. Decyzja o sharding, CDN lub innym storage
powinna wynikać z pomiaru cache hit ratio, QPS primary, rozmiaru indeksu i SLO.

Podstawowe rachunki warto wykonywać jawnie:

```text
average RPS = requests per day / 86 400
peak RPS    = average RPS × peak factor
origin RPS  = total RPS × (1 - cache hit ratio)
storage     = records × average record bytes × replication factor
bandwidth   = RPS × average response bytes
```

Średnia nie służy do wymiarowania. Pool połączeń, Redis, broker i primary DB muszą
obsłużyć peak oraz kontrolowaną degradację. Hot key może przeciążyć pojedynczy
shard mimo niskiego globalnego RPS; lokalny cache lub CDN pomaga, o ile polityka
invalidacji nadal spełnia revocation SLO.

## Jak czytać kompromisy tego laboratorium

Każdy mechanizm odpowiada na konkretny failure mode:

| Problem | Naiwne rozwiązanie | Mechanizm w projekcie | Pozostała granica |
|---|---|---|---|
| concurrent alias | `exists` przed `insert` | unique constraint | konflikt trzeba mapować na stabilne API |
| retry create | wykonaj ponownie | marker + fingerprint + response | retencja określa długość gwarancji |
| dual write DB/cache | zapis do obu po kolei | transactional outbox | delivery nadal jest at-least-once |
| stale revocation | usuń tylko lokalny cache | versioned tombstone + TTL | CDN purge zależy od dostawcy |
| duplicate click | sprawdź, potem zapisz | unique `event_id` w SQL | efekty poza transakcją wymagają własnej idempotencji |
| Redis outage | zwróć 500 | fallback / świadomy fail-open | primary i abuse system przyjmują większe obciążenie |
| multi-region reorder | last arrival wins | monotoniczna wersja | active-active potrzebuje reguły konfliktu |

Warto uruchamiać test wskazany dla danego wiersza i następnie celowo usuwać
mechanizm ochronny. W projekcie edukacyjnym kontrprzykład jest często bardziej
wartościowy niż samo zobaczenie zielonego testu.

## Dostępność, backup i disaster recovery

High availability ogranicza przerwy przy typowych awariach instancji lub strefy;
disaster recovery odtwarza system po utracie regionu albo danych. To różne cele.
Przed produkcją trzeba ustalić:

- RPO — ile najnowszych zapisów wolno utracić;
- RTO — jak długo może trwać przywrócenie redirectów i create;
- kolejność odtwarzania: source DB, projekcje lookup, kolejki i analytics;
- czy cache może tymczasowo obsługiwać redirecty, gdy primary jest niedostępne;
- gdzie znajduje się ostatni sprawdzony backup oraz wynik restore drillu.

PostgreSQL wymaga backupu bazowego i archiwizacji WAL/PITR zgodnej z RPO. Redis nie
musi być backupowany, jeśli jest w pełni odbudowywalną projekcją; po restarcie
należy jednak chronić bazę przed cache stampede. Outbox nie może zostać pominięty
w backupie, bo reprezentuje niedostarczone skutki zatwierdzonych transakcji.

Failover single-writer wymaga fencing starego primary. Sam wybór nowego lidera nie
wystarcza: poprzedni region po odzyskaniu sieci nie może nadal przyjmować zapisów.
Po odtworzeniu należy wykonać reconciliation: porównać source rows, wersje projekcji,
pending outbox, liczbę DLQ i próbkę redirectów przed otwarciem pełnego ruchu.
Endpoint administracyjny `POST /api/v1/admin/urls/projections/reconcile` domyślnie
wykonuje dry-run. `repair=true` ponownie aplikuje brakujące, starsze i rozbieżne
rekordy, ale celowo nie nadpisuje projekcji o wersji wyższej niż primary — taki
przypadek wymaga dochodzenia, a nie automatycznego niszczenia dowodu rozbieżności.

## Testy

### Szybka suite bez infrastruktury

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress test
```

Dowodzi kontraktów HTTP, idempotencji sekwencyjnej, statusów, walidacji,
Base62, dokładnej granicy expiry, deduplikacji, ownership i audytu lifecycle,
trusted proxy chain, rotacji sekretów oraz konfliktów single-writer i active-active.
`ProjectionReconciliationServiceTest` dowodzi bezpiecznej granicy automatycznej naprawy.
H2 nie jest dowodem semantyki blokad PostgreSQL ani Lua/RabbitMQ.

### Kontrakty infrastrukturalne

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify
```

Profil uruchamia przez Testcontainers:

- PostgreSQL: migracje, równoległy custom alias, optimistic locking, idempotencję,
  lease `SKIP LOCKED`, poison projection i usunięcie redundantnych indeksów;
- Redis: rzeczywisty atomowy fixed window z TTL;
- PostgreSQL + Redis: aktywny cache → commit blokady → `410` oraz reorder
  `BLOCKED v8 → ACTIVE v7`, który nie wskrzesza linku;
- PostgreSQL + RabbitMQ: duplikaty at-least-once oraz poison record w DLQ.

Brak Dockera powoduje jawne pominięcie tych testów; zwykła suite pozostaje szybka.

### Edge contract

```powershell
cd edge/cloudflare
npm test
```

Test dowodzi, że 5xx nie jest cache'owane, odpowiedź 302 ma poprawne nagłówki,
a query string nie fragmentuje cache dla tego samego short code.

### Load, spike i degradacja

Najpierw utwórz znany `SHORT_CODE`, potem:

```powershell
k6 run -e BASE_URL=http://localhost:8080 -e SHORT_CODE=load-test -e RATE=100 load-tests/redirect.js
```

Scenariusz używa otwartego modelu `constant-arrival-rate`, żeby wolna aplikacja nie
ukrywała popytu przez coordinated omission. Mierzymy p95/p99, błędy, dropped
iterations i saturation. Eksperyment należy przerwać przy przekroczeniu error
budget, zagrożeniu danych albo nasyceniu zależności poza bezpiecznym limitem.

Osobne przebiegi powinny pokazać: warm cache, cold miss, hot key, Redis down,
Rabbit down, spike oraz soak. Wyników liczbowych nie zapisujemy jako uniwersalnych.

## Uruchomienie lokalne

Wymagane są Java 21 oraz Docker dla zależności:

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Podstawowy request:

```http
POST /api/v1/urls HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Idempotency-Key: demo-create-1

{"longUrl":"https://example.com/docs","customAlias":"docs-demo"}
```

## Trade-offy i kiedy zmienić architekturę

- PostgreSQL + Redis wystarcza długo, jeśli write QPS jest mały, a working set
  mieści się w cache. Nie dodawaj NoSQL wyłącznie z powodu nazwy „URL shortener”.
- CDN obniża origin QPS, ale komplikuje revocation. Dla treści wysokiego ryzyka
  bezpieczeństwo może być ważniejsze niż hit ratio.
- Best-effort analytics chroni redirect, ale nie nadaje się do billingu.
- Single writer upraszcza ID i ordering. Active-active opłaca się dopiero przy
  realnym wymaganiu zapisu podczas utraty regionu.
- Fixed window jest tani, lecz pozwala na burst. Token bucket jest lepszy, gdy
  ważna jest płynność ruchu.
- Editable destination zwiększa wymagania dotyczące wersji, ETag, audytu i purge.

## Granice pozostające produkcyjne

Projekt celowo nie integruje konkretnego secret managera, CDN purge API, globalnej
bazy ani systemu geolokalizacji. Te miejsca są oznaczone jako port lub symulacja.
Przed wdrożeniem potrzebne byłyby także: OAuth2/OIDC zamiast tokenu administracji,
fizyczne partycje analytics, automatyczny CDN purge, wykonany
backup/restore drill, tracing bridge/exporter, autoscaling na właściwych sygnałach
oraz test failoveru z fencing starego primary. Projekt implementuje regułę
ownership dla enterprise, lecz źródłem principal w prawdziwym środowisku powinien
być IAM/OIDC, a nie samodzielnie zarządzany API key dla użytkowników końcowych.
