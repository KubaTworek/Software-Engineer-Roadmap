# News Feed — System Design

<!-- reading-guide:start -->
## Ścieżki czytania

| Cel czytania | Punkt wejścia |
| --- | --- |
| Teoria i model problemu | [5. Główne wyzwanie projektowe](#5-główne-wyzwanie-projektowe) |
| Implementacja i jej dowody | [55. Mapa wykonywalnej implementacji](#55-mapa-wykonywalnej-implementacji) |
| Uruchomienie i eksperymenty | [59. Strategia testów i operacje](#59-strategia-testów-i-operacje) |
| Ograniczenia i scenariusze awarii | [66. Ewolucja architektury i granice rozwiązania](#66-ewolucja-architektury-i-granice-rozwiązania) |

Wspólne mechanizmy i ich testy znajdziesz w [indeksie pojęć backend-engineering](../../backend-engineering/README.md#globalny-indeks-pojęć). Tutaj śledź ich konsekwencje dla opisywanego systemu.
Diagramy docelowe i warianty rozwoju czytaj razem z mapą implementacji; sam opis architektury nie oznacza, że wszystkie jej komponenty są uruchamiane w laboratorium.
<!-- reading-guide:end -->

## Zacznij tutaj

MinIO w laboratorium jest budowany z [oficjalnego commitu 0d7408fc9969](https://github.com/minio/minio/tree/0d7408fc9969caf07de6a8c3a84f9fbb10a6739e)
przez [docker/minio.Dockerfile](docker/minio.Dockerfile), ponieważ poprzedni obraz
rejestru stał się niedostępny. Testcontainers (jeśli dany test używa MinIO) i Compose
korzystają z lokalnego przepisu budowania; nie jest potrzebne logowanie do Quay.
Pierwszy build wymaga dostępu do GitHub, rejestru obrazów bazowych i modułów Go,
trwa dłużej oraz potrzebuje więcej pamięci niż samo pobranie obrazu. Warstwy
kompilacji są cache'owane przez Docker. Dla Compose użyj `docker compose build minio`
przed uruchomieniem usługi.
To przypięta wersja edukacyjna, nie rekomendacja bezpieczeństwa dla produkcji;
licencja AGPL jest dołączana do obrazu w `/licenses/minio/LICENSE`.

| Pytanie | Odpowiedź |
| --- | --- |
| Najważniejszy niezmiennik | Feed nie może ujawnić posta usuniętego lub niedostępnego dla użytkownika, niezależnie od opóźnienia projekcji. |
| Krytyczny przepływ | Publikacja posta → outbox → fan-out on write/pull dla celebryty → ranking → keyset pagination → odczyt feedu. |
| Rekomendowany test | `mvnw.cmd test` sprawdza szybkie kontrakty; `mvnw.cmd -Pinfrastructure-tests verify` potwierdza pełny przepływ oraz semantykę PostgreSQL, Kafki, Redisa, DynamoDB i OpenSearch na Testcontainers. |
| Świadome ograniczenia | Produkcyjny model ML, CDN, skanowanie mediów, regionalny failover i fizyczne usuwanie danych z backupów pozostają granicami zewnętrznych platform. |

## Spis treści

1. [Cel systemu](#1-cel-systemu)
2. [Założenia produktowe](#2-założenia-produktowe)
3. [Wymagania funkcjonalne](#3-wymagania-funkcjonalne)
4. [Wymagania niefunkcjonalne](#4-wymagania-niefunkcjonalne)
5. [Główne wyzwanie projektowe](#5-główne-wyzwanie-projektowe)
6. [Architektura wysokopoziomowa](#6-architektura-wysokopoziomowa)
7. [Główne komponenty](#7-główne-komponenty)
8. [Architektura danych](#8-architektura-danych)
9. [API Design](#9-api-design)
10. [Flow publikacji posta](#10-flow-publikacji-posta)
11. [Flow pobrania feedu](#11-flow-pobrania-feedu)
12. [Feed generation](#12-feed-generation)
13. [Ranking](#13-ranking)
14. [Cache strategy](#14-cache-strategy)
15. [Storage](#15-storage)
16. [Event-driven architecture](#16-event-driven-architecture)
17. [Idempotencja](#17-idempotencja)
18. [Fan-out workers](#18-fan-out-workers)
19. [Problem celebrytów](#19-problem-celebrytów)
20. [Follow / Unfollow](#20-follow--unfollow)
21. [Usuwanie posta](#21-usuwanie-posta)
22. [Prywatność i uprawnienia](#22-prywatność-i-uprawnienia)
23. [Anti-abuse i bezpieczeństwo](#23-anti-abuse-i-bezpieczeństwo)
24. [Observability](#24-observability)
25. [Reliability](#25-reliability)
26. [Consistency model](#26-consistency-model)
27. [Skalowanie](#27-skalowanie)
28. [Liczniki reakcji](#28-liczniki-reakcji)
29. [Timeline freshness](#29-timeline-freshness)
30. [Search](#30-search)
31. [Machine Learning / rekomendacje](#31-machine-learning--rekomendacje)
32. [Feature Store](#32-feature-store)
33. [Data pipeline](#33-data-pipeline)
34. [A/B testing](#34-ab-testing)
35. [Moderacja w feedzie](#35-moderacja-w-feedzie)
36. [Sponsored content](#36-sponsored-content)
37. [Feed Mixer](#37-feed-mixer)
38. [Empty feed problem](#38-empty-feed-problem)
39. [Multi-region design](#39-multi-region-design)
40. [Disaster Recovery](#40-disaster-recovery)
41. [Rate limiting](#41-rate-limiting)
42. [Security](#42-security)
43. [Link Preview](#43-link-preview)
44. [Proponowany stack technologiczny](#44-proponowany-stack-technologiczny)
45. [MVP vs wersja skalowalna](#45-mvp-vs-wersja-skalowalna)
46. [Najważniejsze trade-offy](#46-najważniejsze-trade-offy)
47. [Diagram logiczny](#47-diagram-logiczny)
48. [Request path dla feedu](#48-request-path-dla-feedu)
49. [Write path dla posta](#49-write-path-dla-posta)
50. [Minimalny model scoringu](#50-minimalny-model-scoringu)
51. [Największe ryzyka](#51-największe-ryzyka)
52. [Rekomendowana architektura końcowa](#52-rekomendowana-architektura-końcowa)
53. [Decyzje do obrony na rozmowie System Design](#53-decyzje-do-obrony-na-rozmowie-system-design)
54. [Finalna rekomendacja](#54-finalna-rekomendacja)
55. [Mapa wykonywalnej implementacji](#55-mapa-wykonywalnej-implementacji)
56. [Pojemność i koszt fan-outu](#56-pojemność-i-koszt-fan-outu)
57. [Gwarancje dostarczania i odbudowa](#57-gwarancje-dostarczania-i-odbudowa)
58. [Ranking jako system uczący się](#58-ranking-jako-system-uczący-się)
59. [Strategia testów i operacje](#59-strategia-testów-i-operacje)
60. [Granice bezpieczeństwa i retencji](#60-granice-bezpieczeństwa-i-retencji)
61. [Model kosztowy feedu](#61-model-kosztowy-feedu)
62. [Spójność, kolejność i niezmienniki](#62-spójność-kolejność-i-niezmienniki)
63. [Kolejki, backpressure i przeciążenie](#63-kolejki-backpressure-i-przeciążenie)
64. [Model danych projektowany od access patterns](#64-model-danych-projektowany-od-access-patterns)
65. [Teoria systemu rekomendacyjnego](#65-teoria-systemu-rekomendacyjnego)
66. [Ewolucja architektury i granice rozwiązania](#66-ewolucja-architektury-i-granice-rozwiązania)
67. [Trust & Safety i workflow moderacji](#67-trust--safety-i-workflow-moderacji)
68. [Rekoncyliacja, replay i naprawa projekcji](#68-rekoncyliacja-replay-i-naprawa-projekcji)
69. [Cykl życia danych i prawo do usunięcia](#69-cykl-życia-danych-i-prawo-do-usunięcia)
70. [Eksperyment pojemności i realistyczny rozkład ruchu](#70-eksperyment-pojemności-i-realistyczny-rozkład-ruchu)
71. [Macierz gwarancji i testów](#71-macierz-gwarancji-i-testów)

---

## 1. Cel systemu

System ma dostarczać użytkownikowi spersonalizowany, szybki i aktualny feed treści.

Feed powinien obsługiwać:

- publikowanie postów, artykułów lub newsów,
- obserwowanie autorów, tematów lub źródeł,
- personalizację kolejności treści,
- paginację typu infinite scroll,
- lajki, komentarze, zapisy i udostępnienia,
- ranking i rekomendacje,
- wysoką dostępność,
- bardzo szybki odczyt feedu,
- opóźnione, ale skalowalne przetwarzanie zdarzeń.

---

## 2. Założenia produktowe

Zakładamy produkt podobny do feedu społecznościowego/newsowego.

Użytkownik może:

- stworzyć konto,
- obserwować innych użytkowników, źródła lub tematy,
- publikować treści,
- przeglądać swój feed,
- reagować na treści,
- zgłaszać treści,
- otrzymywać rekomendacje,
- wyszukiwać posty lub artykuły.

Treść może być:

- postem tekstowym,
- artykułem,
- linkiem,
- zdjęciem,
- wideo,
- treścią sponsorowaną,
- rekomendacją algorytmiczną.

---

## 3. Wymagania funkcjonalne

### Feed

System powinien umożliwiać:

1. Pobranie feedu użytkownika.
2. Paginację feedu.
3. Odświeżanie feedu.
4. Ukrywanie już przeczytanych treści.
5. Mieszanie treści obserwowanych i rekomendowanych.
6. Ranking po czasie, jakości, popularności i preferencjach użytkownika.
7. Obsługę świeżych treści niemal w czasie rzeczywistym.

### Publikowanie

Autor może:

1. Dodać post.
2. Edytować post.
3. Usunąć post.
4. Dodać media.
5. Oznaczyć temat, kategorię i tagi.
6. Ustawić widoczność treści.

### Relacje

Użytkownik może:

1. Obserwować autora.
2. Przestać obserwować autora.
3. Obserwować temat.
4. Zablokować autora.
5. Wyciszyć temat.

### Interakcje

Użytkownik może:

1. Lajkować.
2. Komentować.
3. Udostępniać.
4. Zapisywać.
5. Kliknąć w link.
6. Oznaczyć jako „nie interesuje mnie”.
7. Zgłosić treść.

---

## 4. Wymagania niefunkcjonalne

### Wydajność

Docelowe parametry:

- pobranie pierwszej strony feedu: poniżej 200 ms z cache,
- pobranie kolejnej strony: poniżej 300 ms,
- publikacja posta: poniżej 500 ms dla użytkownika,
- propagacja posta do feedów: od kilku sekund do kilkudziesięciu sekund,
- ranking online: lekki, cięższe modele offline lub asynchronicznie.

### Skalowalność

System powinien obsłużyć:

- miliony użytkowników,
- setki milionów relacji follow,
- miliony publikacji dziennie,
- bardzo częste odczyty feedu,
- nierówny rozkład popularności autorów.

### Dostępność

Feed powinien być bardziej dostępny niż idealnie spójny.

Priorytet:

1. Użytkownik powinien prawie zawsze móc odczytać feed.
2. Nowe posty mogą pojawić się z opóźnieniem.
3. Liczniki reakcji mogą być eventual consistent.
4. Ranking może być okresowo mniej aktualny.

### Spójność

System nie musi być silnie spójny globalnie.

Akceptowalne:

- opóźnione pojawienie się posta w feedzie,
- chwilowo nieaktualna liczba polubień,
- feed zależny od cache,
- asynchroniczne usuwanie treści z prekomputowanych feedów.

Nieakceptowalne:

- wyświetlanie prywatnej treści nieuprawnionemu użytkownikowi,
- ignorowanie blokad użytkowników,
- trwałe duplikaty w feedzie,
- utrata opublikowanych treści.

---

## 5. Główne wyzwanie projektowe

Najważniejszy problem to sposób generowania feedu.

Istnieją trzy klasyczne podejścia:

### 5.1 Pull model — fan-out on read

Feed generowany jest w momencie odczytu.

Gdy użytkownik otwiera feed:

1. Pobieramy listę obserwowanych autorów lub tematów.
2. Pobieramy ich najnowsze posty.
3. Łączymy, filtrujemy i sortujemy.
4. Zwracamy wynik.

Zalety:

- prostszy zapis,
- dobry dla autorów z ogromną liczbą obserwujących,
- aktualny feed,
- brak masowego zapisu przy publikacji.

Wady:

- wolny odczyt przy dużej liczbie obserwowanych,
- kosztowne sortowanie,
- trudniejsza personalizacja,
- większe obciążenie przy każdym wejściu użytkownika.

Dobre dla:

- użytkowników obserwujących niewiele źródeł,
- celebrytów lub popularnych autorów,
- systemów z mniejszym ruchem.

### 5.2 Push model — fan-out on write

Feed jest prekomputowany przy publikacji posta.

Gdy autor publikuje post:

1. System znajduje jego obserwujących.
2. Wstawia ID posta do feed inbox każdego obserwującego.
3. Przy odczycie użytkownik pobiera gotową listę postów.

Zalety:

- bardzo szybki odczyt,
- feed gotowy wcześniej,
- dobry dla większości zwykłych autorów,
- łatwa paginacja.

Wady:

- kosztowna publikacja dla popularnych autorów,
- problem „celebrytów” z milionami obserwujących,
- duże zużycie storage,
- trudniejsze usuwanie lub edycja propagowanych treści.

Dobre dla:

- większości użytkowników,
- feedów społecznościowych,
- produktów z częstym odczytem i rzadszą publikacją.

### 5.3 Hybrid model — rekomendowany

Najlepszy wybór dla większego systemu News Feed.

Stosujemy:

- **push** dla zwykłych autorów,
- **pull** dla bardzo popularnych autorów,
- **ranking/rekomendacje** jako osobną warstwę,
- **cache** dla najczęściej odczytywanych feedów.

Czyli:

- jeśli autor ma mniej niż określony próg followersów, np. 100 tys., robimy fan-out on write,
- jeśli autor ma bardzo dużo followersów, nie kopiujemy posta do wszystkich feedów,
- przy odczycie feedu dociągamy najnowsze posty popularnych autorów dynamicznie,
- potem mieszamy wyniki i rankingujemy.

To najbardziej realistyczna architektura dla większej skali.

---

## 6. Architektura wysokopoziomowa

```text
Client
  |
API Gateway
  |
Backend Services
  |
  |-- Auth Service
  |-- User Service
  |-- Post Service
  |-- Media Service
  |-- Follow Service
  |-- Feed Service
  |-- Ranking Service
  |-- Recommendation Service
  |-- Interaction Service
  |-- Notification Service
  |-- Moderation Service
  |
Message Broker / Event Bus
  |
Async Workers
  |
Databases / Cache / Search / Object Storage
```

---

## 7. Główne komponenty

### 7.1 API Gateway

Odpowiada za:

- routing requestów,
- rate limiting,
- authentication middleware,
- request tracing,
- podstawową walidację,
- throttling,
- ochronę przed nadużyciami.

Przykładowe technologie:

- NGINX,
- Envoy,
- Kong,
- AWS API Gateway,
- Cloudflare Workers.

### 7.2 Auth Service

Odpowiada za:

- logowanie,
- rejestrację,
- tokeny JWT lub session tokens,
- refresh tokeny,
- OAuth,
- role użytkownika,
- blokady kont.

Dane:

```text
users
sessions
auth_providers
user_security_settings
```

### 7.3 User Service

Zarządza profilem użytkownika.

Odpowiada za:

- dane profilu,
- ustawienia języka,
- lokalizację,
- preferencje feedu,
- status konta,
- prywatność.

### 7.4 Follow Service

Zarządza grafem relacji.

Odpowiada za:

- follow,
- unfollow,
- followers,
- following,
- block,
- mute,
- topic follow.

To jeden z krytycznych komponentów, bo feed zależy od grafu relacji.

Dane można trzymać w:

- Cassandra / DynamoDB dla dużej skali,
- PostgreSQL dla mniejszej skali,
- Neo4j tylko jeśli potrzebne są złożone zapytania grafowe,
- Redis jako cache followers/following.

### 7.5 Post Service

Odpowiada za:

- tworzenie posta,
- edycję,
- usuwanie,
- widoczność,
- status moderacji,
- metadane,
- powiązanie z mediami.

Post Service nie powinien sam generować feedów synchronicznie. Po zapisaniu posta publikuje event.

Przykład eventu:

```json
{
  "event_type": "post.created",
  "post_id": "p_123",
  "author_id": "u_456",
  "created_at": "2026-06-05T10:15:00Z",
  "visibility": "public",
  "topics": ["ai", "startups"],
  "language": "pl"
}
```

### 7.6 Feed Service

Najważniejszy komponent.

Odpowiada za:

- pobieranie feedu,
- paginację,
- mieszanie źródeł,
- deduplikację,
- filtrowanie blokad i wyciszeń,
- integrację z Ranking Service,
- cache feedu,
- fallbacki.

Feed Service powinien zwracać głównie listę postów z metadanymi, ale nie powinien sam przechowywać całej treści posta. Przechowuje raczej referencje:

```text
user_id -> [post_id, score, source, created_at]
```

### 7.7 Ranking Service

Ranking Service ustala kolejność treści.

Sygnały rankingowe:

- świeżość,
- relacja użytkownika z autorem,
- popularność posta,
- liczba komentarzy,
- liczba kliknięć,
- prawdopodobieństwo zainteresowania,
- język użytkownika,
- tematy obserwowane,
- poprzednie interakcje użytkownika,
- negatywne sygnały, np. ukrycia, zgłoszenia, szybkie przewinięcia.

Ranking może mieć dwie warstwy:

1. **Candidate generation** — wybór kandydatów.
2. **Scoring/ranking** — sortowanie kandydatów.

### 7.8 Recommendation Service

Odpowiada za treści spoza grafu follow.

Może rekomendować:

- popularne artykuły,
- tematy,
- autorów,
- treści podobne do poprzednio klikanych,
- treści lokalne,
- treści trending.

Nie powinien dominować nad feedem, jeśli produkt opiera się na relacjach follow.

Przykładowe proporcje:

```text
70% treści z obserwowanych źródeł
20% rekomendacje tematyczne
10% trendy / eksploracja
```

### 7.9 Interaction Service

Zbiera zdarzenia:

- like,
- comment,
- share,
- save,
- click,
- impression,
- dwell time,
- hide,
- report.

Te dane są potrzebne do:

- liczników,
- rankingów,
- rekomendacji,
- antyspamu,
- analityki.

Interakcje powinny być zapisywane jako eventy, np. do Kafka, a potem agregowane asynchronicznie.

### 7.10 Media Service

Odpowiada za:

- upload obrazów i wideo,
- generowanie miniaturek,
- transkodowanie,
- walidację plików,
- antywirus i bezpieczeństwo,
- przechowywanie w object storage,
- CDN.

Media nie powinny być przechowywane w bazie relacyjnej.

Typowy układ:

```text
Client -> Pre-signed Upload URL -> Object Storage -> CDN
```

### 7.11 Moderation Service

Odpowiada za:

- filtrowanie spamu,
- wykrywanie treści nielegalnych,
- zgłoszenia użytkowników,
- automatyczne klasyfikatory,
- ręczną moderację,
- ukrywanie treści,
- shadow banning,
- ograniczenia dystrybucji.

Moderacja musi być uwzględniana przy feedzie. Nawet jeśli post znajduje się w prekomputowanym feedzie, przed pokazaniem należy sprawdzić jego status.

### 7.12 Notification Service

Może wysyłać:

- powiadomienia push,
- email,
- in-app notifications,
- digesty.

Nie każdy nowy post powinien generować notyfikację. Feed i notifications to osobne systemy.

---

## 8. Architektura danych

### 8.1 User

```sql
users (
  id UUID PRIMARY KEY,
  username TEXT UNIQUE,
  display_name TEXT,
  email TEXT UNIQUE,
  status TEXT,
  language TEXT,
  country TEXT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)
```

### 8.2 Post

```sql
posts (
  id UUID PRIMARY KEY,
  author_id UUID,
  content TEXT,
  content_type TEXT,
  visibility TEXT,
  language TEXT,
  status TEXT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  deleted_at TIMESTAMP
)
```

Status może mieć wartości:

```text
draft
published
under_review
limited
removed
deleted
```

### 8.3 Post Media

```sql
post_media (
  id UUID PRIMARY KEY,
  post_id UUID,
  media_url TEXT,
  media_type TEXT,
  width INT,
  height INT,
  duration_seconds INT,
  thumbnail_url TEXT,
  created_at TIMESTAMP
)
```

### 8.4 Follow

Dla dużej skali lepszy jest storage typu wide-column / key-value.

Model:

```text
following:{user_id} -> [target_id, followed_at]
followers:{target_id} -> [user_id, followed_at]
```

W SQL:

```sql
follows (
  follower_id UUID,
  followee_id UUID,
  created_at TIMESTAMP,
  PRIMARY KEY (follower_id, followee_id)
)
```

Indeks:

```sql
CREATE INDEX followee_id_idx ON follows(followee_id);
```

Przy bardzo dużej skali SQL może stać się wąskim gardłem.

### 8.5 Feed Inbox

Prekomputowany feed użytkownika.

```text
feed:{user_id} -> sorted set [
  post_id,
  score,
  created_at,
  source
]
```

W Redis:

```text
ZADD feed:u_123 1717580000 post:p_456
```

W Cassandrze / DynamoDB:

```text
partition key: user_id
sort key: created_at DESC
columns: post_id, author_id, score, source
```

Przykład tabeli:

```sql
user_feed (
  user_id UUID,
  created_at TIMESTAMP,
  post_id UUID,
  author_id UUID,
  source TEXT,
  score DOUBLE,
  PRIMARY KEY (user_id, created_at, post_id)
)
```

### 8.6 Interactions

```sql
post_interactions (
  post_id UUID,
  user_id UUID,
  type TEXT,
  created_at TIMESTAMP,
  PRIMARY KEY (post_id, user_id, type)
)
```

Dla eventów:

```text
interaction_events
- event_id
- user_id
- post_id
- type
- timestamp
- metadata
```

### 8.7 Aggregates

```text
post_stats (
  post_id,
  like_count,
  comment_count,
  share_count,
  impression_count,
  click_count,
  report_count,
  updated_at
)
```

Liczników nie warto aktualizować synchronicznie przy każdym lajku w głównej bazie, bo szybko stanie się to bottleneckiem.

Lepsze podejście:

```text
Interaction Event -> Kafka -> Aggregation Worker -> Redis / OLAP / DB
```

---

## 9. API Design

### 9.1 Pobranie feedu

```http
GET /v1/feed?limit=20&cursor=abc
Authorization: Bearer <token>
```

Response:

```json
{
  "items": [
    {
      "post_id": "p_123",
      "author": {
        "id": "u_456",
        "display_name": "Anna Nowak",
        "avatar_url": "https://cdn.example.com/avatar.jpg"
      },
      "content": {
        "type": "article",
        "text": "Treść posta...",
        "media": []
      },
      "stats": {
        "likes": 123,
        "comments": 12,
        "shares": 5
      },
      "viewer_state": {
        "liked": false,
        "saved": true,
        "hidden": false
      },
      "created_at": "2026-06-05T10:00:00Z",
      "ranking_reason": "Because you follow AI"
    }
  ],
  "next_cursor": "eyJ0Ijoi..."
}
```

Cursor powinien być opaque, czyli klient nie powinien znać jego struktury.

### 9.2 Publikacja posta

```http
POST /v1/posts
Authorization: Bearer <token>
Content-Type: application/json
```

Body:

```json
{
  "content": "Nowy artykuł o AI...",
  "content_type": "text",
  "visibility": "public",
  "topics": ["ai", "technology"],
  "media_ids": []
}
```

Response:

```json
{
  "post_id": "p_123",
  "status": "published"
}
```

### 9.3 Follow

```http
POST /v1/users/{user_id}/follow
```

```http
DELETE /v1/users/{user_id}/follow
```

### 9.4 Reakcja

```http
POST /v1/posts/{post_id}/like
```

```http
DELETE /v1/posts/{post_id}/like
```

### 9.5 Ukrycie posta

```http
POST /v1/posts/{post_id}/hide
```

Body:

```json
{
  "reason": "not_interested"
}
```

---

## 10. Flow publikacji posta

```text
1. Client wysyła POST /posts.
2. API Gateway waliduje token.
3. Post Service zapisuje post w bazie.
4. Post Service publikuje event post.created.
5. Event trafia do Kafka / Pulsar / RabbitMQ.
6. Feed Fanout Worker odbiera event.
7. Worker pobiera followers autora.
8. Dla zwykłego autora zapisuje post_id do feed inbox followersów.
9. Dla popularnego autora oznacza post jako pull-based.
10. Ranking/Recommendation pipeline aktualizuje indeksy.
11. Notification Service opcjonalnie wysyła powiadomienia.
```

Krytyczna decyzja: publikacja posta nie powinna czekać na pełny fan-out. Użytkownik powinien dostać odpowiedź po zapisie posta i wysłaniu eventu.

---

## 11. Flow pobrania feedu

```text
1. Client wysyła GET /feed.
2. Feed Service pobiera precomputed feed z Redis / Cassandra.
3. Pobiera dodatkowe posty popularnych autorów w modelu pull.
4. Pobiera kandydatów rekomendacyjnych.
5. Usuwa treści zablokowane, wyciszone, usunięte, prywatne.
6. Usuwa duplikaty.
7. Ranking Service oblicza score.
8. Feed Service pobiera szczegóły postów z Post Service / cache.
9. Pobiera statystyki i viewer_state.
10. Zwraca posortowany wynik z cursorem.
```

---

## 12. Feed generation

### 12.1 Candidate sources

Feed powinien składać się z kandydatów z kilku źródeł:

```text
A. Followed authors
B. Followed topics
C. Popular authors pull-based
D. Recommendations
E. Trending
F. Sponsored content
G. Editorial / curated content
```

Potem wszystkie źródła trafiają do rankera.

### 12.2 Deduplikacja

Ten sam post może pojawić się z kilku źródeł:

- autor obserwowany,
- temat obserwowany,
- trending,
- rekomendacja.

Trzeba deduplikować po `post_id`.

Przy artykułach z zewnętrznych źródeł warto też mieć deduplikację po canonical URL lub content hash.

### 12.3 Cursor-based pagination

Nie używamy offset pagination.

Złe:

```http
GET /feed?page=10&limit=20
```

Dlaczego?

- feed się zmienia,
- offset jest wolny dla dużych list,
- możliwe duplikaty i pominięcia.

Lepsze:

```http
GET /feed?cursor=opaque_token&limit=20
```

Cursor może zawierać:

```json
{
  "last_score": 0.872,
  "last_created_at": "2026-06-05T10:00:00Z",
  "seen_post_ids_hash": "...",
  "session_id": "feed_session_123"
}
```

Można też utrzymywać krótkotrwałą sesję feedu w Redis, żeby użytkownik podczas scrollowania widział stabilny snapshot.

---

## 13. Ranking

Prosty ranking może wyglądać tak:

```text
score =
  freshness_score
  + affinity_score
  + engagement_score
  + topic_match_score
  + quality_score
  - negative_feedback_score
  - spam_score
```

### Freshness

Nowe treści mają boost, ale nie powinny zawsze wygrywać.

```text
freshness_score = exp(-age_hours / decay_factor)
```

### Affinity

Jak mocno użytkownik jest związany z autorem:

- często klika,
- komentuje,
- lajkuje,
- długo czyta,
- obserwuje od dawna,
- ma wspólne tematy.

### Engagement

Popularność posta:

- komentarze,
- udostępnienia,
- CTR,
- dwell time,
- zapisania.

Surowe lajki są podatne na promowanie clickbaitu. Lepiej używać jakościowych metryk, np. czasu czytania, zapisów i komentarzy wysokiej jakości.

### Negative feedback

Silny sygnał:

- hide,
- report,
- block,
- szybkie przewinięcie,
- „not interested”.

### Ranking online vs offline

Nie wszystko powinno być liczone w request path.

#### Online

W czasie requestu można liczyć:

- świeżość,
- proste score,
- filtry użytkownika,
- viewer_state,
- deduplikację,
- lekkie reguły biznesowe.

#### Offline / nearline

Asynchronicznie warto liczyć:

- embeddingi użytkowników,
- embeddingi treści,
- podobieństwa,
- jakość autora,
- reputację źródła,
- trendy,
- modele ML,
- antyspam,
- predykcję CTR.

---

## 14. Cache strategy

Cache jest krytyczny, bo feed jest odczytywany bardzo często.

### 14.1 Redis / Memcached

Przechowujemy:

- feed inbox użytkownika,
- post summary,
- user profile summary,
- post stats,
- follow graph cache,
- viewer state,
- trending lists.

Przykładowe klucze:

```text
feed:user:{user_id}
post:{post_id}:summary
post:{post_id}:stats
user:{user_id}:profile
following:{user_id}
followers:{user_id}
trending:{country}:{topic}
```

### 14.2 Cache invalidation

Najtrudniejsze przypadki:

- usunięcie posta,
- blokada autora,
- zmiana prywatności,
- wykrycie spamu,
- edycja posta,
- unfollow.

Strategia:

1. Przy odczycie zawsze sprawdzać status posta.
2. Nie usuwać natychmiast wszystkiego z feedów, jeśli to drogie.
3. Dla krytycznych zmian, np. usunięcie lub prywatność, mieć denylistę / status check.
4. Cache z krótkim TTL dla ryzykownych danych.
5. Asynchroniczne cleanup jobs.

---

## 15. Storage

### 15.1 PostgreSQL / MySQL

Dobre dla:

- użytkowników,
- postów,
- metadanych,
- transakcyjnych danych,
- konfiguracji,
- moderacji.

### 15.2 Cassandra / DynamoDB

Dobre dla:

- feed inbox,
- follow graph,
- dużych append-only struktur,
- wysokiego throughputu zapisu.

### 15.3 Redis

Dobre dla:

- cache,
- ranking candidates,
- hot feeds,
- sesji feedu,
- rate limiting.

Redis nie powinien być jedynym trwałym źródłem feedu, jeśli utrata danych jest niedopuszczalna.

### 15.4 Kafka / Pulsar

Dobre dla:

- eventów,
- fan-out,
- interaction tracking,
- ranking pipeline,
- analytics,
- retry.

### 15.5 Elasticsearch / OpenSearch

Dobre dla:

- wyszukiwania postów,
- wyszukiwania autorów,
- filtrowania po tekście,
- discovery.

### 15.6 Object Storage + CDN

Dobre dla:

- zdjęć,
- wideo,
- miniaturek,
- załączników.

Przykład:

```text
S3 / GCS / Azure Blob + CloudFront / Cloudflare CDN
```

---

## 16. Event-driven architecture

Kluczowe eventy:

```text
post.created
post.updated
post.deleted
user.followed
user.unfollowed
user.blocked
post.liked
post.unliked
post.commented
post.shared
post.viewed
post.clicked
post.hidden
post.reported
media.uploaded
moderation.post_removed
ranking.score_updated
```

Eventy powinny być idempotentne.

Każdy event powinien mieć:

```json
{
  "event_id": "evt_123",
  "event_type": "post.created",
  "entity_id": "p_123",
  "actor_id": "u_456",
  "timestamp": "2026-06-05T10:00:00Z",
  "version": 1
}
```

---

## 17. Idempotencja

Bardzo ważne przy fan-out.

Worker może przetworzyć ten sam event więcej niż raz.

Dlatego zapis do feedu powinien być idempotentny:

```text
(user_id, post_id) jako unikalna para
```

Albo w Redis sorted set:

```text
ZADD feed:user_id score post_id
```

`ZADD` z tym samym memberem nie stworzy duplikatu.

---

## 18. Fan-out workers

### Proces

```text
1. Worker odbiera post.created.
2. Sprawdza autora.
3. Pobiera liczbę followersów.
4. Jeśli autor nie jest celebrytą:
   - dzieli followersów na batche,
   - zapisuje post do ich feedów.
5. Jeśli autor jest celebrytą:
   - zapisuje post do author_recent_posts,
   - nie robi masowego fan-out.
```

### Batchowanie

Nie można próbować zapisać miliona feedów jednym requestem.

Przykład:

```text
batch size: 500–5000 followersów
parallel workers: zależnie od infrastruktury
retry: exponential backoff
dead letter queue: tak
```

---

## 19. Problem celebrytów

Jeśli autor ma 50 mln obserwujących, fan-out on write jest bardzo kosztowny.

Rozwiązanie:

```text
celebrity_posts:{author_id} -> latest posts
```

Przy pobraniu feedu:

1. Pobieramy feed inbox użytkownika.
2. Sprawdzamy, czy obserwuje popularnych autorów.
3. Pobieramy ich najnowsze posty.
4. Mieszamy z feedem.

Można trzymać osobną listę `high_fanout_authors`.

---

## 20. Follow / Unfollow

### Follow

Kiedy użytkownik zaczyna obserwować autora:

Opcje:

1. Nie dodawać starych postów do feedu.
2. Dodać ostatnie N postów autora.
3. Dodać tylko przyszłe posty.

Najlepsze UX zwykle daje opcja 2:

```text
Po follow dodajemy ostatnie np. 20 postów autora do feedu użytkownika.
```

### Unfollow

Po unfollow:

Opcje:

1. Natychmiast usunąć posty autora z feedu.
2. Zostawić, ale filtrować przy odczycie.
3. Asynchronicznie wyczyścić.

Rekomendacja:

- przy odczycie filtrować,
- asynchronicznie czyścić feed.

---

## 21. Usuwanie posta

Problem: post może być już skopiowany do tysięcy lub milionów feedów.

Nie robimy synchronicznego usuwania ze wszystkich feedów.

Lepszy flow:

```text
1. Post Service oznacza post jako deleted/removed.
2. Publikuje post.deleted.
3. Feed Service przy odczycie filtruje deleted posts.
4. Async cleanup usuwa referencje z feedów.
```

To daje szybkie ukrycie bez kosztownej operacji online.

---

## 22. Prywatność i uprawnienia

Przy każdym feed item trzeba respektować:

- widoczność posta,
- blokady,
- wyciszenia,
- prywatne konta,
- kraj / wiek / ograniczenia prawne,
- status moderacji,
- relację follow.

Nie wystarczy polegać na tym, że feed został dobrze wygenerowany. Uprawnienia muszą być sprawdzane także przy odczycie.

---

## 23. Anti-abuse i bezpieczeństwo

System powinien mieć:

- rate limiting publikacji,
- rate limiting follow/unfollow,
- wykrywanie spam kont,
- wykrywanie botów,
- limity komentarzy,
- limity zgłoszeń,
- reputację autora,
- reputację domeny/linku,
- skanowanie URL-i,
- skanowanie mediów,
- blokowanie złośliwych linków,
- audyt działań moderatorów.

---

## 24. Observability

Trzeba mierzyć osobno jakość feedu, backend i pipeline.

### Metryki techniczne

- feed read latency p50/p95/p99,
- cache hit ratio,
- liczba itemów filtrowanych,
- fan-out lag,
- Kafka consumer lag,
- error rate,
- timeout rate,
- DB read/write latency,
- Redis memory usage,
- ranking service latency.

### Metryki produktowe

- CTR,
- dwell time,
- scroll depth,
- hide rate,
- report rate,
- follow conversion,
- return rate,
- session length,
- diversity feedu,
- udział rekomendacji,
- udział treści obserwowanych,
- freshness feedu.

### Alerty

- feed p95 > 500 ms,
- consumer lag > próg,
- spadek cache hit ratio,
- wzrost report rate,
- brak nowych postów w feedzie,
- wzrost błędów 5xx,
- wzrost pustych feedów.

---

## 25. Reliability

### Retry

Każdy worker powinien obsługiwać:

- retry z backoffem,
- dead letter queue,
- idempotencję,
- metryki failed events.

### Degradacja

Jeśli Ranking Service jest niedostępny:

- użyj prostego sortowania po czasie,
- pokaż cached feed,
- ogranicz rekomendacje.

Jeśli Recommendation Service padnie:

- pokaż tylko follow feed,
- dodaj trending fallback.

Jeśli Redis padnie:

- odczyt z trwałego storage,
- większa latencja, ale system działa.

Jeśli Post Stats są niedostępne:

- pokaż feed bez liczników lub z opóźnionymi licznikami.

---

## 26. Consistency model

Proponowany model:

```text
Posts: strong consistency dla zapisu podstawowego
Feed: eventual consistency
Stats: eventual consistency
Recommendations: eventual consistency
Moderation: strong enforcement at read time
Privacy: strong enforcement at read time
```

Najważniejsza zasada:

> Można pokazać feed nieidealnie posortowany, ale nie wolno pokazać treści, której użytkownik nie powinien zobaczyć.

---

## 27. Skalowanie

### Poziome skalowanie usług

Każdy serwis powinien być stateless poza bazami i cache.

```text
Feed Service: horizontal scale
Post Service: horizontal scale
Ranking Service: horizontal scale
Workers: horizontal scale
```

### Partycjonowanie feedu

Dla feed inbox:

```text
partition key = user_id
sort key = created_at / score
```

Użytkownicy są naturalną partycją.

### Partycjonowanie follow graph

```text
followers:{author_id}
following:{user_id}
```

Dla celebrytów trzeba shardować listę followersów:

```text
followers:{author_id}:{shard_id}
```

### Hot keys

Problem:

- bardzo popularny autor,
- trending post,
- viral content,
- duże liczniki.

Rozwiązania:

- shardowane liczniki,
- cache lokalny,
- CDN,
- celebrity pull model,
- batch aggregation.

---

## 28. Liczniki reakcji

Nie aktualizujemy głównego rekordu posta przy każdym lajku.

Lepszy model:

```text
Like request
  -> Interaction Service
  -> zapis user-post like
  -> event post.liked
  -> Redis counter increment
  -> async aggregate to DB
```

Dla bardzo popularnych postów można użyć shardowanych liczników:

```text
post_like_count:{post_id}:{shard_id}
```

Odczyt sumuje shardy albo korzysta z okresowo agregowanej wartości.

---

## 29. Timeline freshness

Feed powinien być świeży, ale niekoniecznie aktualizowany w każdej milisekundzie.

Można użyć podejścia:

- feed główny jest stabilny podczas sesji,
- nowe posty trafiają do osobnego bufora,
- UI pokazuje „X nowych postów”,
- użytkownik klika, aby odświeżyć.

To zapobiega przeskakiwaniu feedu podczas czytania.

---

## 30. Search

Search nie powinien korzystać z głównej bazy postów.

Flow:

```text
post.created / post.updated
  -> event
  -> Search Indexer
  -> OpenSearch / Elasticsearch
```

Search index zawiera:

- post_id,
- autor,
- tekst,
- tagi,
- język,
- status,
- created_at,
- engagement score.

Przy wynikach search trzeba ponownie sprawdzić uprawnienia.

---

## 31. Machine Learning / rekomendacje

Na początek nie trzeba budować ciężkiego ML.

### Etap 1 — rules-based

- świeżość,
- follow graph,
- popularność,
- tematy.

### Etap 2 — feature-based ranking

- user-topic affinity,
- author affinity,
- engagement prediction,
- negative feedback.

### Etap 3 — embeddings

- embedding użytkownika,
- embedding posta,
- podobieństwo,
- ANN index, np. FAISS, ScaNN, Milvus, Pinecone.

### Etap 4 — learning-to-rank

- model rankingowy,
- A/B testing,
- feature store,
- online inference.

---

## 32. Feature Store

Przy większej skali warto mieć Feature Store.

Przykładowe feature’y:

```text
user_avg_session_length
user_topic_affinity_ai
author_quality_score
post_ctr_1h
post_ctr_24h
post_report_rate
post_embedding
user_embedding
```

Offline store:

- BigQuery,
- Snowflake,
- S3 + Spark,
- Databricks.

Online store:

- Redis,
- DynamoDB,
- Cassandra.

---

## 33. Data pipeline

```text
Client events
  -> Event Collector
  -> Kafka
  -> Stream Processing
  -> Real-time aggregates
  -> Data Lake
  -> Batch jobs
  -> Feature Store
  -> Ranking models
```

Technologie:

- Kafka / Pulsar,
- Flink / Spark Streaming,
- Airflow / Dagster,
- BigQuery / Snowflake,
- S3 / GCS,
- dbt,
- Redis / DynamoDB jako online feature store.

---

## 34. A/B testing

Feed bez A/B testów to ryzykowny produkt, bo ranking bezpośrednio wpływa na zachowanie użytkowników.

System powinien obsługiwać:

- eksperymenty rankingowe,
- różne proporcje rekomendacji,
- różne decay factors,
- różne modele,
- holdout group,
- guardrail metrics.

Guardrails:

- report rate nie może wzrosnąć,
- hide rate nie może wzrosnąć,
- latency nie może się pogorszyć,
- diversity nie może spaść poniżej progu,
- nie można promować spamu lub clickbaitu.

---

## 35. Moderacja w feedzie

Moderacja powinna działać w kilku miejscach.

### Przed publikacją

- walidacja treści,
- skan URL,
- skan mediów,
- klasyfikator spamu.

### Po publikacji

- monitoring zgłoszeń,
- automatyczne obniżanie dystrybucji,
- review queue.

### Przy odczycie feedu

- finalny status check,
- blokady,
- ograniczenia kraju / wieku,
- prywatność.

---

## 36. Sponsored content

Jeżeli system ma reklamy lub promowane treści, nie należy mieszać ich bez kontroli z organicznym rankingiem.

Lepiej mieć osobny Ads Candidate Source:

```text
Organic candidates
Recommendation candidates
Sponsored candidates
```

Następnie Feed Mixer decyduje:

```text
max 1 sponsored item per N organic items
frequency capping
brand safety
user targeting constraints
```

---

## 37. Feed Mixer

Feed Mixer to komponent, który łączy różne źródła.

Przykład:

```text
Input:
- 100 postów z follow graph
- 50 rekomendacji
- 20 trending
- 10 sponsored

Process:
- eligibility filtering
- deduplication
- ranking
- diversity rules
- business rules
- pagination

Output:
- 20 itemów feedu
```

Reguły diversity:

- nie więcej niż 2 posty tego samego autora pod rząd,
- nie więcej niż 3 posty z jednego tematu,
- nie więcej niż 1 reklama na ekran,
- wymieszanie świeżych i popularnych treści.

---

## 38. Empty feed problem

Nowy użytkownik nie ma jeszcze relacji.

Rozwiązania:

- onboarding tematów,
- wybór zainteresowań,
- popularne treści lokalne,
- rekomendowani autorzy,
- trending,
- editorial picks,
- import kontaktów, jeżeli produkt to uzasadnia.

Nowy użytkownik powinien dostać feed od razu, nawet bez follow graph.

---

## 39. Multi-region design

Dla dużego systemu:

### Odczyty

- feed cache regionalny,
- CDN dla mediów,
- read replicas,
- lokalne Redis clusters.

### Zapisy

Trudniejsze, bo trzeba uniknąć konfliktów.

Możliwe podejścia:

1. Single primary region dla zapisu.
2. Multi-region active-active z conflict resolution.
3. Partycjonowanie użytkowników po regionie.

Dla pierwszej wersji:

```text
single write region + regional read replicas/cache
```

Dla bardzo dużej skali:

```text
regional ownership użytkowników
```

---

## 40. Disaster Recovery

Warto mieć:

- backup baz,
- backup event logu,
- replay Kafka events,
- możliwość odbudowy feedów,
- runbooki,
- testy odtwarzania,
- multi-AZ deployment.

Feed inbox można traktować jako dane częściowo odtwarzalne, jeśli mamy:

- posty,
- follow graph,
- event log,
- ranking data.

To ważne: feed cache/inbox nie musi być jedyną kopią prawdy.

---

## 41. Rate limiting

Przykłady:

```text
POST /posts: 10/min/user
POST /comments: 30/min/user
POST /follow: 100/day/user
GET /feed: dynamic limit
POST /like: 300/min/user
```

Dodatkowo:

- limity IP,
- limity device fingerprint,
- limity dla nowych kont,
- wyższe limity dla zaufanych użytkowników.

---

## 42. Security

System powinien mieć:

- JWT/session validation,
- CSRF protection dla web,
- CORS policy,
- input sanitization,
- XSS protection,
- SSRF protection przy link preview,
- virus scanning mediów,
- signed URLs,
- encryption at rest,
- encryption in transit,
- audit logs,
- RBAC dla adminów/moderatorów.

Szczególnie groźny obszar: **link preview**. Nie można pozwolić backendowi pobierać dowolnych URL-i bez ochrony przed SSRF.

---

## 43. Link Preview

Jeśli posty mogą zawierać linki:

```text
1. Client wysyła URL.
2. Link Preview Service waliduje URL.
3. Sprawdza blacklisty i DNS.
4. Pobiera stronę przez sandbox/proxy.
5. Parsuje OpenGraph metadata.
6. Tworzy preview.
7. Cacheuje wynik.
```

Zabezpieczenia:

- blokada adresów prywatnych,
- timeout,
- limit rozmiaru odpowiedzi,
- brak followowania podejrzanych redirectów,
- izolowany worker.

---

## 44. Proponowany stack technologiczny

Dla wersji produkcyjnej:

```text
Frontend:
- Web / Mobile
- CDN

Backend:
- Go / Java / Kotlin / Node.js / Python
- REST lub GraphQL dla klienta
- gRPC między serwisami

Infra:
- Kubernetes
- Envoy / NGINX
- Kafka
- Redis
- PostgreSQL
- Cassandra / DynamoDB
- OpenSearch
- S3/GCS
- Prometheus + Grafana
- OpenTelemetry
- Jaeger/Tempo
```

Dla MVP:

```text
Backend monolith modularny
PostgreSQL
Redis
S3-compatible storage
Background jobs
OpenSearch opcjonalnie
```

Nie zaczynałbym od mikroserwisów, jeśli zespół jest mały.

---

## 45. MVP vs wersja skalowalna

### MVP

W MVP wystarczy:

```text
- modularny monolit
- PostgreSQL
- Redis
- background worker
- prosty fan-out on write
- feed sorted by created_at + simple score
- object storage dla mediów
```

MVP flow:

```text
post.created -> worker -> insert do feedów followersów
GET /feed -> Redis/Postgres -> hydrate posts -> return
```

### Wersja skalowalna

Później:

```text
- Kafka
- Cassandra/DynamoDB dla feedu
- celebrity pull model
- Ranking Service
- Recommendation Service
- Feature Store
- ML ranking
- multi-region
```

---

## 46. Najważniejsze trade-offy

### Fan-out on write vs fan-out on read

Rekomendacja: hybrid.

### Redis vs trwały storage dla feedu

Redis świetny jako cache, ale feed inbox dla dużego systemu lepiej mieć też w trwałym storage.

### Ranking synchroniczny vs asynchroniczny

Ciężki ranking offline/nearline, lekki ranking online.

### Usuwanie z feedów

Nie usuwać synchronicznie z milionów feedów. Filtrować przy odczycie i sprzątać asynchronicznie.

### Mikroserwisy vs monolit

Dla MVP: modularny monolit.

Dla dużej skali: wyodrębnione serwisy.

---

## 47. Diagram logiczny

```text
                           +----------------+
                           |    Clients     |
                           +--------+-------+
                                    |
                                    v
                           +----------------+
                           |  API Gateway   |
                           +--------+-------+
                                    |
        +---------------------------+---------------------------+
        |                           |                           |
        v                           v                           v
+---------------+           +---------------+           +---------------+
| Auth Service  |           |  Feed Service |           | Post Service  |
+---------------+           +-------+-------+           +-------+-------+
                                    |                           |
                                    v                           v
                            +---------------+           +---------------+
                            | Ranking Svc   |           | Post DB       |
                            +-------+-------+           +-------+-------+
                                    |                           |
                                    v                           v
                            +---------------+           +---------------+
                            | Redis Cache   |           | Event Bus     |
                            +-------+-------+           +-------+-------+
                                    |                           |
                                    v                           v
                            +---------------+           +---------------+
                            | Feed Storage  |<----------| Fanout Worker |
                            +---------------+           +---------------+
                                                            |
                                                            v
                                                    +---------------+
                                                    | Follow Store  |
                                                    +---------------+
```

---

## 48. Request path dla feedu

```text
GET /feed

Feed Service:
1. user_id z tokena
2. pobierz feed candidates z Redis
3. jeśli cache miss, pobierz z Feed Storage
4. pobierz celebrity candidates
5. pobierz recommendation candidates
6. przefiltruj blocked/muted/deleted/private
7. deduplikuj
8. ranking
9. hydrate post details
10. dołącz viewer_state i stats
11. zwróć items + next_cursor
```

---

## 49. Write path dla posta

```text
POST /posts

Post Service:
1. validate request
2. check user status
3. save post
4. publish post.created
5. return post_id

Async:
1. moderation check
2. fan-out
3. indexing search
4. update recommendations
5. update notifications
```

---

## 50. Minimalny model scoringu

Na start wystarczy coś prostego:

```text
score =
  0.45 * freshness
+ 0.25 * author_affinity
+ 0.15 * topic_match
+ 0.10 * engagement
+ 0.05 * source_quality
- 0.50 * negative_feedback
```

Nie należy przywiązywać się do wag. Trzeba je testować eksperymentalnie.

---

## 51. Największe ryzyka

### 1. Feed będzie wolny

Rozwiązanie:

- prekomputowany feed,
- Redis,
- pagination cursor,
- ograniczenie liczby kandydatów,
- hydracja batchowa.

### 2. Celebryci zabiją fan-out

Rozwiązanie:

- hybrid feed,
- celebrity pull model.

### 3. Ranking wypromuje clickbait

Rozwiązanie:

- negative feedback,
- quality score,
- report rate,
- dwell time,
- diversity rules.

### 4. Prywatne treści wyciekną

Rozwiązanie:

- permission check at read time,
- testy bezpieczeństwa,
- denylisty,
- status check.

### 5. Liczniki staną się bottleneckiem

Rozwiązanie:

- eventy,
- shardowane countery,
- async aggregation.

### 6. Feed będzie pełen duplikatów

Rozwiązanie:

- dedup po post_id,
- dedup po URL/content hash,
- session-level seen set.

---

## 52. Rekomendowana architektura końcowa

Dla produkcyjnego systemu:

```text
1. Hybrid feed generation.
2. Fan-out on write dla zwykłych autorów.
3. Fan-out on read dla popularnych autorów.
4. Redis jako hot cache.
5. Cassandra/DynamoDB jako feed storage.
6. PostgreSQL dla postów/użytkowników/metadanych.
7. Kafka jako event bus.
8. OpenSearch dla search.
9. Object Storage + CDN dla mediów.
10. Ranking Service z lekkim online scoringiem.
11. Offline/nearline pipeline dla rekomendacji i feature’ów.
12. Moderation check przy odczycie feedu.
```

---

## 53. Decyzje do obrony na rozmowie System Design

1. **Hybrid fan-out**, bo czysty push nie skaluje się dla celebrytów, a czysty pull jest za wolny dla dużych feedów.
2. **Event-driven pipeline**, bo publikacja, ranking, search, fan-out i analytics nie powinny być synchroniczne.
3. **Cursor pagination**, bo offset pagination nie pasuje do dynamicznego feedu.
4. **Read-time permission check**, bo cache/feed inbox może być nieaktualny.
5. **Asynchroniczne liczniki**, bo synchroniczne counter updates szybko staną się bottleneckiem.
6. **Oddzielenie candidate generation od ranking**, bo to upraszcza rozwój rekomendacji.
7. **Redis jako cache, nie jedyne źródło prawdy**, bo hot feed musi być szybki, ale odtwarzalny.
8. **Deduplikacja i diversity rules**, bo sam ranking może tworzyć słabe UX.
9. **Graceful degradation**, bo feed powinien działać nawet przy awarii rekomendacji/rankingu.
10. **Moderacja jako część request path**, bo bezpieczeństwo jest ważniejsze niż cache hit ratio.

---

## 54. Finalna rekomendacja

Najlepszy projekt dla News Feed to **hybrydowa architektura feedu oparta o eventy**:

```text
Post Service zapisuje treść
Kafka rozprowadza eventy
Fanout Workers prekomputują feed dla zwykłych autorów
Popularni autorzy są obsługiwani pull-based
Feed Service miesza kandydatów z follow graph, rekomendacji i trendów
Ranking Service sortuje kandydatów
Redis przyspiesza hot path
Cassandra/DynamoDB przechowuje trwały feed inbox
PostgreSQL przechowuje dane transakcyjne
Moderation i permission checks działają przy odczycie
```

Taka architektura daje dobry kompromis między szybkością, skalowalnością, świeżością i kontrolą jakości feedu.

---

## 55. Mapa wykonywalnej implementacji

Ten projekt nie jest wyłącznie katalogiem klas odpowiadających elementom diagramu. Poniższa tabela wskazuje kanoniczną ścieżkę i zapobiega myleniu modelu edukacyjnego z gwarancją produkcyjną.

| Problem | Kanoniczna implementacja | Source of truth | Projekcja / cache |
| --- | --- | --- | --- |
| zapis posta | `PostService` + `outbox.DomainEventPublisher` | PostgreSQL `posts`, `domain_events` | Kafka dopiero po commicie |
| dostarczenie zdarzenia | `OutboxWorker` z lease, retry i synchronicznym ack brokera | `domain_events` | topic Kafki |
| idempotencja konsumenta | `IdempotentEventProcessor` | `(consumer_name, event_id)` | osobny marker dla każdej projekcji |
| feed zwykłego autora | `FanoutWorker` przechodzący wszystkie strony shardu | post + graf follow | `feed_inbox` |
| feed celebryty | `CelebrityService` z histerezą 100%/80% | graf follow + post | `celebrity_posts`, pull przy odczycie |
| prywatność | `ContentAccessPolicy` po hydracji | status posta, visibility, follow, block | wpis inbox/cache nigdy nie jest autoryzacją |
| stabilna paginacja | `FeedSessionService` + `FeedSessionCursor` | snapshot rankingu sesji | Redis z ograniczonym TTL |
| wyszukiwanie | `SearchIndexWorker` | PostgreSQL | odtwarzalny indeks OpenSearch |

Krytyczna zasada brzmi: **projekcja może być nieaktualna, ale nie może samodzielnie przyznać dostępu**. Dlatego post jest ponownie hydrowany i sprawdzany tuż przed serializacją odpowiedzi, również po cache hit.

## 56. Pojemność i koszt fan-outu

Przykładowe obliczenie wymusza jawne założenia zamiast „magicznych” liczb:

- 20 mln DAU, średnio 2 publikacje dziennie na aktywnego autora daje około 463 postów/s średnio;
- pik 10× oznacza około 4,6 tys. publikacji/s;
- przy medianie 200 obserwujących czysty push tworzy około 926 tys. wpisów inbox/s w piku;
- jeden autor z 10 mln obserwujących wygenerowałby jednorazowo 10 mln zapisów i długi ogon kolejki;
- wpis inbox o efektywnym rozmiarze 80 B to około 74 MiB/s przed replikacją i narzutem indeksów.

To uzasadnia model hybrydowy. Próg celebrity nie jest uniwersalną stałą: powinien wynikać z budżetu opóźnienia fan-outu, przepustowości partycji i kosztu dodatkowego pull/merge. Histereza zapobiega przełączaniu strategii przy oscylacji licznika. Przy democji projekcja pull jest usuwana; produkcyjnie potrzebny jest również kontrolowany backfill do inboxów albo okres przejściowy z odczytem obu źródeł.

## 57. Gwarancje dostarczania i odbudowa

| Awaria | Oczekiwane zachowanie | Mechanizm |
| --- | --- | --- |
| proces umiera po zapisie posta | event nadal oczekuje | transakcyjny outbox |
| relay umiera po claimie | event wraca po wygaśnięciu lease | czasowy `CLAIMED` |
| broker przyjął event, relay nie oznaczył rekordu | możliwy duplikat, nie utrata | at-least-once + idempotent consumer |
| jedna projekcja już obsłużyła event | inne nadal mogą go obsłużyć | klucz `(consumer_name,event_id)` |
| shard followersów ma więcej niż batch | worker czyta następne strony | keyset po `follower_id` |
| cache zawiera usunięty/prywatny post | wynik zostaje odfiltrowany | read-time access policy |
| indeks wyszukiwania zostaje utracony | można odtworzyć go z source of truth | replay/backfill, indeks nie jest prawdą |

Kafka daje kolejność w obrębie partycji, nie globalnie. Klucz `postId` utrzymuje zdarzenia jednego posta razem. Exactly-once end-to-end nie jest tutaj obiecywane: broker, baza konsumenta i zewnętrzny indeks nie dzielą jednej transakcji. Projekt osiąga praktycznie użyteczną kombinację **at-least-once transport + idempotentne efekty**.

Rekoncyliacja powinna okresowo porównywać źródło prawdy z projekcjami: brakujące wpisy odtworzyć, osierocone usunąć, a metrykę rozbieżności wystawić do alertowania. Backfill musi mieć limit przepustowości, checkpoint i możliwość wznowienia, aby nie zagłodził ruchu online.

## 58. Ranking jako system uczący się

Pipeline ma cztery osobne etapy: retrieval kandydatów, batchowe pobranie cech, scoring i reguły post-rankingu (deduplikacja, różnorodność, sponsored caps). Złożenie ich w jedną funkcję utrudnia pomiar recall oraz diagnozę, czy słaby wynik pochodzi z braku kandydata, czy z niewłaściwej kolejności.

Offline mierzymy co najmniej recall@K, NDCG i kalibrację. Online patrzymy na CTR, dwell time, retencję oraz guardraile: hide/report rate, latency p99 i udział treści od jednego autora. Sam CTR promuje clickbait. Log treningowy musi zawierać ekspozycje, nie tylko kliknięcia; inaczej model uczy się wyłącznie z treści pokazanych przez poprzedni model. Eksploracja powinna być ograniczona, audytowalna i wyłączalna kill switchem.

Snapshot sesji stabilizuje strony w czasie zmian rankingu. Ceną jest to, że bardzo świeży post nie pojawi się w środku istniejącej sesji; użytkownik zobaczy go po odświeżeniu. Cursor jest nieprzezroczystym uchwytem do snapshotu, a nie offsetem w dynamicznym zbiorze.

## 59. Strategia testów i operacje

```powershell
# szybka pętla: domena, polityki, cursory, outbox oraz wiring na H2
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify

# semantyka zależna od prawdziwego PostgreSQL i Kafki; wymaga Dockera
.\mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify

# ręczny eksperyment wydajnościowy po uruchomieniu środowiska i przygotowaniu tokenu
k6 run -e BASE_URL=http://localhost:8080 -e AUTH_TOKEN=... load-tests/feed.js
```

Test jednostkowy dowodzi reguły lokalnej. Test kontraktu PostgreSQL dowodzi zachowania `ON CONFLICT` i migracji. Test Kafki pokazuje kolejność jednego klucza. Żaden z nich osobno nie dowodzi SLO całego systemu; do tego służą load/stress/spike/soak, metryki nasycenia i kontrolowany game day. Alerty startowe są w `ops/prometheus-alerts.yml`, a kroki diagnozy w `ops/RUNBOOK.md`.

## 60. Granice bezpieczeństwa i retencji

- bearer token jest przechowywany jako SHA-256; surowy sekret jest zwracany tylko przy utworzeniu;
- Redis rate limit używa atomowego Lua `INCR` + `PEXPIRE`, a invalidacja cache korzysta ze `SCAN`, nie blokującego `KEYS`;
- upload ogranicza MIME i rozmiar, lecz produkcyjnie wymaga kwarantanny, AV/CDR, dekodowania po stronie serwera i podpisanych URL;
- blokada w dowolnym kierunku oraz status moderacji są sprawdzane w hot path;
- logi nie mogą zawierać tokenów, pełnego PII ani treści prywatnych; identyfikatory powinny mieć kontrolowaną cardinality;
- soft delete nie jest pełnym usunięciem danych. Polityka retencji musi objąć bazę, cache, indeks, media, event log, analitykę i harmonogram wygaśnięcia backupów;
- dostęp operatora do kolejki moderacji wymaga osobnej roli, audytu i zasady najmniejszych uprawnień. Projekt celowo nie udaje gotowego panelu Trust & Safety.

---

## 61. Model kosztowy feedu

Najważniejsza decyzja w systemie feedu nie brzmi „Kafka czy RabbitMQ”, lecz **kiedy zapłacić za złożenie timeline’u**. Koszt można przenieść na zapis, na odczyt albo rozdzielić pomiędzy oba momenty.

### Fan-out on write

Po publikacji tworzymy wpis w inboxie każdego obserwującego. Przybliżony koszt pojedynczego posta wynosi:

```text
write_cost(post) ≈ follower_count(author) × replication_factor × index_amplification
```

Zaletą jest tani i przewidywalny odczyt: feed jest już przygotowany. Wadą jest write amplification, szczególnie dla kont o bardzo dużej liczbie obserwujących. Koszt ponosimy także dla użytkowników nieaktywnych, którzy nigdy nie odczytają wygenerowanego wpisu.

Model push jest dobry, gdy:

- większość autorów ma umiarkowaną liczbę obserwujących;
- odczytów feedu jest znacznie więcej niż publikacji;
- wymagane jest niskie p99 odczytu;
- eventual consistency rzędu sekund jest akceptowalna.

### Fan-out on read

Podczas requestu pobieramy ostatnie posty obserwowanych autorów, wykonujemy merge i ranking. Koszt odczytu rośnie w przybliżeniu wraz z liczbą obserwowanych źródeł oraz głębokością pobrania:

```text
read_cost(user) ≈ followed_sources × candidates_per_source + ranking_cost
```

Nie generujemy pracy dla nieaktywnych użytkowników, ale hot path staje się kosztowny i podatny na długi ogon opóźnień. Samo równoległe odpytanie wielu shardów nie usuwa problemu: latency całego requestu jest zbliżone do latency najwolniejszego wymagającego źródła.

### Model hybrydowy

Projekt stosuje push dla zwykłych autorów i pull dla celebrytów. Wyniki obu ścieżek są deduplikowane i rankingowane razem. Próg przejścia powinien uwzględniać:

- liczbę zapisów, które worker może wykonać w budżecie freshness;
- rozkład liczby obserwujących, a nie tylko średnią;
- aktywność odbiorców;
- koszt storage i replikacji;
- częstotliwość publikacji autora;
- dopuszczalny koszt merge podczas odczytu.

Próg bez histerezy powoduje flapping. Dlatego autor wchodzi do modelu pull przy 100% progu, a wraca do push dopiero poniżej 80%. Zmiana strategii jest migracją danych: samo przełączenie flagi nie backfilluje brakujących inboxów.

### Write amplification kontra read amplification

To ogólny wzorzec systemów rozproszonych. Prekomputacja obniża koszt odczytu kosztem większej liczby zapisów, miejsca i trudniejszej invalidacji. Obliczanie na żądanie redukuje storage, lecz zwiększa latency i zależność od dostępności wielu źródeł. Feed jest szczególnym przypadkiem materialized view, a `feed_inbox` jest projekcją zoptymalizowaną pod jeden access pattern.

## 62. Spójność, kolejność i niezmienniki

Nie wystarczy powiedzieć, że system jest „eventually consistent”. Trzeba wskazać **które dane**, **dla kogo**, **jak długo** i **jaki błąd jest dopuszczalny**.

### Rodzaje obserwowanej spójności

- **read-your-writes** — autor po utworzeniu posta powinien móc go odczytać z source of truth, nawet jeśli fan-out jeszcze trwa;
- **monotonic reads** — klient nie powinien zobaczyć nowszego stanu, a następnie starszego wskutek zmiany repliki;
- **eventual consistency** — post może pojawić się w inboxie po kilku sekundach;
- **strong authorization check** — aktualna blokada, widoczność lub usunięcie muszą wygrać ze starą projekcją;
- **bounded staleness** — biznes może określić maksymalny akceptowany fan-out lag, np. 30 sekund.

Z tego wynika kluczowe rozdzielenie: **świeżość feedu może być słaba, poufność nie**. Brak świeżego posta jest czasowym problemem UX. Ujawnienie prywatnego posta jest naruszeniem bezpieczeństwa.

### Kolejność zdarzeń

Kafka gwarantuje kolejność w partycji. Dlatego zdarzenia dotyczące tego samego agregatu powinny mieć ten sam klucz, np. `postId`. Globalna kolejność wszystkich postów nie jest potrzebna i ograniczyłaby skalowanie do jednej partycji.

Nawet w jednej partycji konsument musi radzić sobie z semantyką domenową:

```text
post.created(v1) → post.updated(v2) → post.deleted(v3)
```

Jeżeli replay lub backfill dostarczy starszy stan po nowszym, projekcja powinna znać wersję agregatu albo traktować delete jako tombstone. Sama idempotencja event ID usuwa duplikaty, ale nie rozwiązuje reorderingu dwóch różnych zdarzeń.

### Safety i liveness

Niezmienniki dzielą się na dwie kategorie:

- **safety** — coś złego nigdy nie może się wydarzyć, np. zablokowany użytkownik nie widzi posta;
- **liveness** — coś dobrego w końcu powinno się wydarzyć, np. opublikowany post ostatecznie trafi do feedów.

Outbox, retry i replay wspierają liveness. `ContentAccessPolicy`, status posta i blokady chronią safety. System może chwilowo poświęcić liveness podczas awarii, ale nie powinien osłabiać kontroli dostępu tylko po to, aby zwrócić pełniejszy feed.

### CAP i PACELC w tym systemie

Podział sieci zmusza do wyboru na konkretnej granicy, nie dla całej aplikacji naraz:

- publikacja źródłowego posta preferuje spójność PostgreSQL;
- cache feedu preferuje dostępność i może być nieaktualny;
- read-time authorization preferuje poprawność — przy braku danych potrzebnych do decyzji powinien działać fail-closed;
- rekomendacje mogą zniknąć i zdegradować się do following/trending.

PACELC przypomina, że również bez awarii istnieje kompromis latency–consistency. Synchroniczne czekanie na wszystkie projekcje poprawiłoby read-your-writes feedu, ale znacząco zwiększyłoby latency publikacji i coupling.

## 63. Kolejki, backpressure i przeciążenie

Kafka wygładza krótkie skoki, ale nie tworzy nieograniczonej przepustowości. Jeśli tempo produkcji `λ` przez dłuższy czas przekracza tempo konsumpcji `μ`, lag rośnie bez ograniczenia:

```text
lag_growth_per_second = max(0, λ - μ)
recovery_time ≈ accumulated_lag / (μ - λ_normal)
```

Zwiększenie retencji Kafki daje więcej czasu na naprawę, ale nie rozwiązuje braku capacity. Consumer group skaluje się maksymalnie do liczby partycji; dodatkowy konsument ponad tę liczbę pozostaje bez pracy.

### Mechanizmy ochronne

- bounded batch i ograniczony czas przetwarzania;
- pauzowanie partycji zamiast pobierania kolejnych rekordów przy niedostępnym downstreamie;
- retry z exponential backoff i jitterem;
- retry topic lub DLT dla poison record, aby jedna wiadomość nie blokowała partycji bez końca;
- limit współbieżności po stronie bazy i OpenSearch;
- osobny budżet dla ruchu online oraz replay/backfill;
- load shedding dla pracy opcjonalnej, np. rekomendacji lub kosztownego debug explanation.

Retry bez limitu jest mnożnikiem obciążenia. Gdy zależność zwalnia, rośnie liczba requestów w locie, timeouty generują retry, a retry zwiększają kolejkę — powstaje retry storm. Dlatego timeout, retry, circuit breaker, bulkhead i capacity limit muszą być projektowane jako jeden mechanizm, a nie niezależne adnotacje.

### Priorytety degradacji

Przy przeciążeniu system powinien zachować kolejność wartości:

1. kontrola dostępu i usunięcia;
2. możliwość opublikowania źródłowego posta;
3. podstawowy feed following;
4. świeżość projekcji;
5. rekomendacje, ciężki ranking, sponsored content i dodatkowe explanation.

Graceful degradation nie oznacza zwracania dowolnych starych danych. Oznacza świadome wyłączenie funkcji niższego priorytetu przy zachowaniu najważniejszych niezmienników.

## 64. Model danych projektowany od access patterns

W systemie o tej skali nie zaczynamy od uniwersalnego diagramu encji. Najpierw zapisujemy najważniejsze zapytania, ich wolumen i wymagane sortowanie.

| Access pattern | Klucz / porządek | Model |
| --- | --- | --- |
| pobierz następne N wpisów feedu użytkownika | `user_id`, malejąco `created_at, post_id` | `feed_inbox` |
| pobierz najnowsze posty celebrytów obserwowanych przez użytkownika | zbiór `author_id`, malejąco `created_at` | `celebrity_posts` |
| znajdź followersów autora partiami | `followee_id, shard_id`, rosnąco `follower_id` | `follower_shards` |
| sprawdź ponowne przetworzenie projekcji | `consumer_name, event_id` | `processed_kafka_events` |
| znajdź zdarzenia gotowe do publikacji | `status, next_attempt_at` | `domain_events` |
| wyszukaj tekstowo | odwrócony indeks | OpenSearch, nie PostgreSQL feed query |

Denormalizacja jest celowa. Ten sam `postId` może wystąpić w source of truth, inboxach, cache, indeksie wyszukiwania i danych rankingowych. Nie próbujemy atomowo aktualizować wszystkiego. Ustalamy właściciela danych i sposób odbudowy każdej pochodnej.

### Partycjonowanie i hot partitions

Partycjonowanie wyłącznie po `user_id` daje dobrą lokalność odczytu feedu, ale popularny użytkownik może stać się hot key. Shardowanie followersów rozprasza fan-out, lecz zwiększa koszt koordynacji i merge. Losowy shard dobrze rozkłada zapis, ale utrudnia uporządkowany odczyt; shard wyprowadzony z identyfikatora daje deterministyczny routing.

Partycja nie powinna być ani zbyt duża, ani zbyt drobna. Za duża ogranicza równoległość i recovery, za mała zwiększa metadata overhead, liczbę połączeń i koszt merge. Dobór wymaga danych o rozkładzie, nie tylko średniej.

### Indeksy i paginacja

Indeks `(user_id, created_at DESC, post_id DESC)` odpowiada dokładnie keyset pagination. `post_id` rozstrzyga remisy czasu. Offset wymaga przejścia i odrzucenia wcześniejszych wierszy, a przy równoległych insertach powoduje duplikaty lub pominięcia.

W rankowanym feedzie sam cursor czasowy nie wystarcza, ponieważ score może się zmienić pomiędzy requestami. Snapshot sesji zamraża kolejność na krótki okres. Produkcyjne alternatywy to podpisany cursor zawierający wersję rankingu, materialized session albo deterministic scoring oparty o wersjonowane cechy.

## 65. Teoria systemu rekomendacyjnego

System rekomendacyjny nie jest pojedynczym modelem sortującym wszystkie posty. Typowy pipeline zawiera:

```text
eligible corpus
  → candidate retrieval
  → feature hydration
  → lightweight ranking
  → policy filters
  → re-ranking/diversity
  → logging ekspozycji
```

### Retrieval

Retrieval ma znaleźć kilkaset lub kilka tysięcy potencjalnie trafnych elementów z ogromnego korpusu. Źródłami mogą być graf follow, podobieństwo embeddingów, popularność w kohorcie, tematy i świeże treści. Najważniejszą metryką retrieval jest recall: ranker nie może uratować posta, którego nie otrzymał jako kandydata.

### Ranking

Ranker przewiduje użyteczność kandydata w konkretnym kontekście. Feature’y dzielą się na:

- cechy użytkownika — zainteresowania, historia, locale;
- cechy posta — wiek, jakość, temat, ryzyko spamu;
- cechy relacji — affinity użytkownik–autor;
- cechy kontekstu — pora, urządzenie, pozycja i typ sesji.

Feature używany online musi mieć tę samą semantykę jak podczas treningu. Training-serving skew powstaje, gdy pipeline offline liczy cechę inaczej niż serwis online albo używa informacji z przyszłości.

### Bias i pętla zwrotna

Model obserwuje reakcje tylko na treści, które poprzedni model pokazał. To selection bias. Pozycja sama wpływa na prawdopodobieństwo kliknięcia, więc surowy CTR nie jest czystą oceną jakości. Potrzebne są logi ekspozycji, korekta position bias, ograniczona eksploracja i eksperymenty.

Pętla zwrotna może zawężać treść do coraz bardziej podobnych elementów. Diversity, novelty i limity autora nie są kosmetyką UX, lecz częścią jakości systemu. Cel optymalizacji powinien uwzględniać negatywne sygnały oraz długoterminową satysfakcję, nie tylko natychmiastowe kliknięcie.

### Eksperymenty

A/B test wymaga stabilnego przypisania użytkownika, rozłącznych wariantów, z góry określonej metryki głównej i guardraili. Nie należy zatrzymywać eksperymentu dokładnie w chwili uzyskania korzystnego wyniku. Trzeba uwzględnić sezonowość, novelty effect, sample ratio mismatch oraz koszt wielokrotnego testowania hipotez.

Model można wdrożyć najpierw w shadow mode: liczyć wyniki bez wpływu na użytkownika i porównywać rozkłady, latency oraz błędy. Canary ogranicza blast radius, ale automatyczny rollback powinien reagować zarówno na błędy techniczne, jak i guardraile produktowe.

## 66. Ewolucja architektury i granice rozwiązania

Rozsądny system nie zaczyna od docelowego zestawu technologii. Każdy etap powinien odpowiadać obserwowanemu ograniczeniu.

| Etap | Architektura | Sygnał do przejścia dalej |
| --- | --- | --- |
| 1 | PostgreSQL, chronological pull feed | query i p99 przestają mieścić się w budżecie |
| 2 | indeksy, keyset pagination, krótki cache | koszt składania feedu dominuje odczyt |
| 3 | fan-out on write i outbox | asynchroniczna projekcja wymaga niezawodnego transportu |
| 4 | Kafka, idempotentni konsumenci, retry/DLT | liczba workerów i niezależnych projekcji rośnie |
| 5 | hybrid fan-out i shardy followersów | celebrity/hot key dominuje write amplification |
| 6 | retrieval, ranking, eksperymenty, feature store | produkt potrzebuje personalizacji, nie tylko chronologii |
| 7 | regionalizacja i DR | wymagania dostępności uzasadniają koszt operacyjny |

Obecna implementacja jest modularnym laboratorium. Pokazuje granice i kontrakty, ale nie dowodzi automatycznie gotowości do obsługi milionów użytkowników. W szczególności:

- PostgreSQL `feed_inbox` demonstruje model dostępu; DynamoDB wymaga osobnego capacity planning, retry i obsługi throttlingu;
- lokalny scoring demonstruje pipeline; produkcyjny model potrzebuje wersjonowania, registry, monitoringu driftu i rollbacku;
- read-time policy chroni podstawowy invariant; pełny produkt wymaga tenantów, jurysdykcji, polityk wieku i audytu decyzji;
- outbox gwarantuje brak utraty pomiędzy bazą i brokerem przy założeniach PostgreSQL; nie daje exactly-once dla wszystkich efektów zewnętrznych;
- deklaracja multi-region nie zastępuje testu failoveru, pomiaru RPO/RTO ani rozwiązania konfliktów zapisu;
- test obciążeniowy jest scenariuszem startowym, a nie dowodem capacity bez realistycznych danych, cache hit ratio i infrastruktury podobnej do produkcji.

Najważniejsza umiejętność System Design to nie zapamiętanie diagramu. Jest nią przejście od wymagań i niezmienników przez liczby oraz access patterns do najprostszego rozwiązania, a następnie wskazanie jego punktu załamania i bezpiecznej ścieżki ewolucji.

## 67. Trust & Safety i workflow moderacji

Moderacja nie jest pojedynczym filtrem tekstu. To osobny proces decyzyjny, którego wynik wpływa na source of truth, projekcje, wyszukiwarkę, cache i rekomendacje. Minimalny przepływ wygląda następująco:

```text
publikacja → automatyczna klasyfikacja → allow / review / reject
                                      ↓
                           kolejka decyzji operatora
                                      ↓
                     publikacja albo ukrycie + audyt + event
```

Klasyfikator zwraca werdykt oraz uzasadnienie. Treść wymagająca oceny otrzymuje stan `PENDING_REVIEW` i nie może być widoczna tylko dlatego, że stara projekcja pozostała w cache. Uprawniony operator podejmuje decyzję dokładnie raz; blokada pesymistyczna rekordu review zapobiega dwóm sprzecznym decyzjom. Decyzja aktualizuje post, zapisuje niezmienny audit log i publikuje event przez outbox w tej samej transakcji.

### Najważniejsze pojęcia

- **false positive** usuwa legalną treść i szkodzi autorowi; **false negative** przepuszcza treść szkodliwą;
- próg klasyfikatora jest decyzją produktową i ryzykiem, a nie wyłącznie parametrem ML;
- moderacja synchroniczna zmniejsza ekspozycję, lecz zwiększa latency publikacji; asynchroniczna skraca write path, ale wymaga stanu kwarantanny;
- reguły powinny być wersjonowane, aby dało się wyjaśnić historyczną decyzję;
- audit log musi wskazywać operatora, czas, decyzję i powód, ale nie powinien bez potrzeby kopiować PII;
- odwołanie jest nową decyzją, nie edycją historii;
- uprawnienia `MODERATOR` i `ADMIN` podlegają zasadzie najmniejszych uprawnień oraz audytowi dostępu.

Implementacja obejmuje role, kolejkę review, atomową decyzję, zmianę statusu posta, invalidację feedu, event domenowy oraz audit log. Nie obejmuje panelu operatorskiego, zewnętrznego modelu ML, systemu apelacji, hash matching ani współpracy z zespołem prawnym. Te elementy należy traktować jako jawne granice Trust & Safety.

## 68. Rekoncyliacja, replay i naprawa projekcji

System zdarzeniowy nie może opierać poprawności wyłącznie na założeniu, że każdy event został przetworzony raz i w kolejności. Projekcja może stracić rekord wskutek błędu konsumenta, ręcznej operacji, wygaśnięcia retencji brokera albo zmiany kodu. Dlatego każda pochodna powinna mieć określone źródło prawdy i procedurę odbudowy.

| Operacja | Cel | Czy wykonuje efekt biznesowy? |
| --- | --- | --- |
| retry | ponowić nieudaną próbę tego samego eventu | nie, efekt musi być idempotentny |
| replay | ponownie odczytać historyczne eventy | nie |
| rebuild | utworzyć projekcję od zera ze źródła prawdy | nie |
| reconciliation | wykryć i naprawić różnicę source–projection | nie |
| backfill | uzupełnić nowe pole lub nową projekcję historycznymi danymi | nie |
| compensating action | odwrócić wcześniej zaakceptowany efekt domenowy | tak, jest nową operacją biznesową |

`ReconciliationService` odbudowuje indeks wyszukiwania z opublikowanych postów oraz feed konkretnego użytkownika z grafu follow i source of truth. Najpierw czyści pochodną, potem zapisuje aktualny stan i unieważnia cache. Endpoint wymaga roli `ADMIN`, ponieważ rebuild jest kosztowną operacją operatorską.

W dużym systemie rebuild nie powinien wykonywać jednego nieograniczonego zapytania. Potrzebuje keyset pagination, checkpointu, ograniczenia przepustowości, osobnego budżetu połączeń, metryk postępu i możliwości wznowienia. Bezpieczny wariant buduje nową wersję indeksu obok starej, porównuje liczbę i próbki dokumentów, a następnie atomowo przełącza alias. Naprawa nie może ponownie wysyłać powiadomień, pobierać płatności ani naliczać statystyk biznesowych.

## 69. Cykl życia danych i prawo do usunięcia

Retencja jest częścią modelu danych, nie nocnym skryptem porządkowym. Każdy zbiór powinien mieć właściciela, podstawę przechowywania, okres retencji, mechanizm usunięcia oraz informację, czy może zostać odbudowany.

| Zbiór | Rola | Przykładowa polityka w laboratorium |
| --- | --- | --- |
| `feed_inbox` | odtwarzalna projekcja | 90 dni |
| `processed_kafka_events` | marker idempotencji | dłużej niż maksymalny replay, tutaj 30 dni |
| przetworzony outbox | historia techniczna | 30 dni |
| upload `PENDING` | porzucona rezerwacja zasobu | 1 dzień |
| soft-deleted post | okno na propagację tombstone | 30 dni przed fizycznym usunięciem |
| audit moderacji | dowód decyzji | według polityki prawnej, osobno od treści |

`DataRetentionService` usuwa wygasłe tokeny, stare inboxy, markery, zakończony outbox i porzucone uploady. Przed fizycznym usunięciem posta usuwa dokument wyszukiwarki. Operację można wywołać ręcznie przez endpoint administracyjny lub włączyć scheduler właściwością konfiguracyjną; domyślnie scheduler jest wyłączony, aby laboratorium nie wykonywało niejawnych destrukcyjnych operacji.

Prawo do usunięcia wymaga propagacji tombstone do cache, indeksu, feature store, analityki, mediów i replik. Backup zwykle nie jest modyfikowany w miejscu: dane wygasają zgodnie z harmonogramem backupu, a po odtworzeniu trzeba ponownie zastosować dziennik usunięć. Legal hold może zatrzymać usunięcie, ale musi być jawny, ograniczony i audytowalny. Anonimizacja jest skuteczna tylko wtedy, gdy nie da się ponownie powiązać danych za pomocą innych identyfikatorów.

## 70. Eksperyment pojemności i realistyczny rozkład ruchu

Średni użytkownik nie reprezentuje systemu społecznościowego. Liczba obserwujących, częstotliwość publikacji i aktywność mają ciężki ogon. Generator `load-tests/generate-zipf-dataset.mjs` tworzy dane zgodne z rozkładem Zipfa, a `feed.js` wybiera użytkowników z podobnym skosem. Osobny `celebrity-spike.js` sprawdza nagły post autora o bardzo dużej liczbie followersów.

Scenariusze mają różne pytania:

- **load** — czy oczekiwany ruch mieści się w SLO;
- **stress** — gdzie znajduje się punkt nasycenia i jak system degraduje się powyżej niego;
- **spike** — czy kolejki i cache pochłaniają nagły skok bez utraty niezmienników;
- **soak** — czy po długim czasie rosną heap, połączenia, lag albo rozmiar projekcji.

Przed eksperymentem zapisujemy capacity hypothesis: RPS, mix read/write, liczbę aktywnych użytkowników, rozkład followersów, cache hit ratio, wielkość payloadu, liczbę partycji i oczekiwane p95/p99. Kryteria przerwania muszą chronić środowisko, np. error rate, lag, saturation poola lub latency przekraczające bezpieczny próg. Raport `capacity-report.json` jest wynikiem konkretnego środowiska, danych i wersji aplikacji — nie uniwersalną liczbą wydajności.

Open workload model utrzymuje tempo przyjść niezależnie od czasu odpowiedzi i ujawnia narastanie kolejki. Closed model czeka na odpowiedź wirtualnego użytkownika, więc wolniejszy system automatycznie generuje mniej requestów i może ukryć przeciążenie. Dla SLO należy unikać coordinated omission: generator powinien uwzględniać requesty, które miały nadejść podczas zastoju.

Szczegółowy protokół eksperymentu, metryki i szablon decyzji capacity znajdują się w `load-tests/CAPACITY_EXPERIMENT.md`.

## 71. Macierz gwarancji i testów

Test jest wartościowy wtedy, gdy wskazuje konkretną gwarancję oraz jej granicę. Liczba testów sama w sobie nie jest miarą jakości systemu.

| Gwarancja | Test / poziom | Czego nie dowodzi |
| --- | --- | --- |
| prywatny, ukryty lub zablokowany post nie wycieka ze starej projekcji | szybki test `ContentAccessPolicy` i `FeedService` | zachowania wszystkich zewnętrznych cache/CDN |
| jedna decyzja moderacji zmienia stan, audyt i event atomowo | test serwisu moderacji | jakości klasyfikatora i procesu apelacji |
| event jest publikowany dopiero po commicie encji | test outboxa + PostgreSQL IT | exactly-once efektu zewnętrznego |
| ten sam event nie tworzy dwóch efektów konsumenta | test idempotentnego procesora | kolejności różnych eventów bez wersji agregatu |
| zdarzenia jednego klucza zachowują kolejność | Kafka IT | globalnej kolejności pomiędzy partycjami |
| wpis feedu ma stabilny porządek i idempotentny zapis | PostgreSQL IT i DynamoDB IT | production capacity i throttlingu DynamoDB |
| limiter Redis wykonuje atomowe `INCR` + TTL | Redis IT | globalnego limitu podczas partycji sieci |
| indeks można upsertować, usunąć i odbudować | OpenSearch IT + test reconciliation | bezbłędnego mappingu dla każdego typu dokumentu |
| cały krytyczny przepływ dociera od posta przez outbox i Kafkę do feedu | `CriticalFeedFlowInfrastructureIT` | multi-region, CDN i awarii procesu w każdym możliwym punkcie |
| polityka retencji usuwa właściwe klasy danych | szybki test `DataRetentionService` | fizycznego wygaśnięcia backupów i legal hold |

Profil szybki używa test doubles lub H2 tam, gdzie badana jest logika aplikacyjna. Profil `infrastructure-tests` uruchamia prawdziwe obrazy PostgreSQL, Kafka, Redis, DynamoDB Local i OpenSearch przez Testcontainers. Rozdzielenie profili utrzymuje szybką pętlę developerską, a jednocześnie nie udaje, że H2 potwierdza blokady PostgreSQL albo że mapa w pamięci zachowuje się jak baza partycjonowana.

Pełne zaznajomienie z projektem oznacza umiejętność obrony nie tylko happy path, ale również: właściciela każdego stanu, modelu spójności, klucza partycjonowania, granicy transakcji, strategii idempotencji, zachowania przy przeciążeniu, odbudowy projekcji, retencji danych, obserwowalności i bezpiecznej degradacji. To właśnie te decyzje — nie sama lista technologii — tworzą system design feedu.
