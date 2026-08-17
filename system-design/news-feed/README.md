# News Feed — System Design

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
