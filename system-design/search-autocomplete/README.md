# Search Autocomplete — System Design

## 1. Cel systemu

System ma podpowiadać użytkownikowi zapytania lub wyniki podczas wpisywania tekstu w polu wyszukiwania.

Przykład:

```text
Użytkownik wpisuje:
iph

System zwraca:
iphone 15
iphone 15 pro
iphone charger
iphone case
iphone 14
```

Autocomplete może zwracać kilka typów sugestii:

1. **Query suggestions** — popularne frazy wyszukiwania.
2. **Entity suggestions** — konkretne produkty, użytkownicy, dokumenty, miasta, firmy.
3. **Category suggestions** — np. `iphone in Electronics`.
4. **Personalized suggestions** — oparte o historię użytkownika.
5. **Spell-corrected suggestions** — np. `iphnoe` → `iphone`.
6. **Trending suggestions** — popularne teraz, niekoniecznie globalnie historycznie.

---

## 2. Wymagania funkcjonalne

System powinien:

- zwracać sugestie po wpisaniu prefiksu,
- działać przy każdej zmianie tekstu w search boxie,
- wspierać ranking sugestii,
- obsługiwać literówki,
- obsługiwać wiele języków,
- filtrować treści niedozwolone,
- uwzględniać lokalizację, język, urządzenie i kontekst,
- wspierać personalizację,
- działać zarówno dla anonimowych, jak i zalogowanych użytkowników,
- pozwalać na szybkie aktualizowanie indeksu,
- zbierać dane o kliknięciach i konwersjach.

---

## 3. Wymagania niefunkcjonalne

Najważniejsze:

| Wymaganie      |                                                        Cel |
|----------------|-----------------------------------------------------------:|
| Latency p95    |                                        < 50 ms server-side |
| Latency p99    |                                       < 100 ms server-side |
| Availability   |                                           99.9% lub 99.99% |
| Throughput     | bardzo wysoki, bo request idzie przy każdym wpisanym znaku |
| Consistency    |                             eventual consistency wystarczy |
| Freshness      |               od sekund do kilku minut, zależnie od domeny |
| Personalizacja |                        najlepiej bez dużego kosztu latency |
| Bezpieczeństwo |                          brak wycieków prywatnych sugestii |
| Koszt          |                     agresywne cache’owanie i prekomputacja |

---

## 4. Szacowanie skali

Załóżmy duży produkt:

- 100 mln MAU,
- 10 mln DAU,
- każdy użytkownik wykonuje średnio 10 sesji wyszukiwania dziennie,
- każda sesja generuje 5–8 requestów autocomplete,
- średnio 500–800 mln requestów autocomplete dziennie.

Średni QPS:

```text
800 mln / 86 400 ≈ 9 260 QPS
```

Peak QPS, np. 5–10x:

```text
50 000–100 000 QPS
```

Autocomplete jest bardzo latency-sensitive, więc system powinien być zaprojektowany pod odczyty, cache i precomputed
indexes.

---

## 5. API

### 5.1 Publiczne API

```http
GET /v1/autocomplete?q=iph&limit=10&locale=en-US&country=US
```

Przykładowa odpowiedź:

```json
{
  "query": "iph",
  "suggestions": [
    {
      "text": "iphone 15",
      "type": "query",
      "score": 0.98
    },
    {
      "text": "iphone 15 pro",
      "type": "query",
      "score": 0.95
    },
    {
      "text": "iphone charger",
      "type": "query",
      "score": 0.88
    }
  ],
  "request_id": "req_123"
}
```

### 5.2 Parametry

| Parametr     | Opis                                     |
|--------------|------------------------------------------|
| `q`          | aktualny tekst wpisany przez użytkownika |
| `limit`      | liczba sugestii                          |
| `locale`     | język użytkownika                        |
| `country`    | kraj                                     |
| `user_id`    | opcjonalnie, zwykle z auth tokena        |
| `session_id` | do personalizacji krótkoterminowej       |
| `device`     | web, iOS, Android                        |
| `context`    | np. kategoria, strona, workspace         |

Nie pakowałbym zbyt dużo logiki do parametrów query. Lepiej część kontekstu przekazywać przez nagłówki lub auth/session
metadata.

---

## 6. Architektura wysokiego poziomu

```text
Client
  |
  | debounce 50–150 ms
  v
CDN / Edge Cache
  |
  v
API Gateway
  |
  v
Autocomplete Service
  |
  +--> L1 in-memory cache
  |
  +--> Distributed cache, np. Redis/Memcached
  |
  +--> Suggestion Index Service
  |       |
  |       +--> Trie / FST / Search Index
  |
  +--> Ranking Service
  |
  +--> Personalization Service
  |
  +--> Policy / Safety Filter
  |
  v
Response
```

Pipeline offline / near-real-time:

```text
Search logs / Click logs / Product catalog / Documents
        |
        v
Event Stream, np. Kafka / Pulsar
        |
        v
Processing, np. Flink / Spark / Beam
        |
        v
Aggregation + Ranking features
        |
        v
Index Builder
        |
        v
Index Storage
        |
        v
Serving Nodes
```

---

## 7. Client-side design

Autocomplete nie powinien wysyłać requestu dla każdego znaku bez kontroli.

Po stronie klienta:

- debounce 50–150 ms,
- minimum prefix length, np. 2 znaki,
- anulowanie poprzednich requestów,
- cache lokalny dla ostatnich prefiksów,
- fallback do ostatnich znanych sugestii,
- ochrona przed race conditions.

Przykład:

```text
Użytkownik wpisuje szybko:

i -> ip -> iph -> ipho
```

Request dla `iph` może wrócić po `ipho`. Klient musi ignorować starsze odpowiedzi, jeśli nie pasują do aktualnego
inputu.

---

## 8. Główne komponenty

### 8.1 Autocomplete Service

Odpowiada za:

- walidację requestu,
- normalizację inputu,
- pobranie kandydatów,
- ranking,
- personalizację,
- filtrowanie,
- formatowanie odpowiedzi,
- logowanie metryk.

To powinien być stateless service, łatwo skalowalny horyzontalnie.

---

### 8.2 Suggestion Index Service

Serwuje kandydatów dla prefiksu.

Możliwe struktury:

#### Opcja A: Trie

Dobre dla klasycznego prefiksowego autocomplete.

```text
i
└── p
    └── h
        └── o
            └── n
                └── e
```

W każdym węźle można trzymać top N sugestii dla danego prefiksu.

Zalety:

- bardzo szybki lookup,
- prosta semantyka prefiksów,
- dobry dla precomputed top suggestions.

Wady:

- duży memory footprint,
- trudniejsze aktualizacje,
- słabsze fuzzy matching,
- problematyczne dla wielu języków, tokenizacji i złożonych rankingów.

---

#### Opcja B: FST — Finite State Transducer

To bardzo dobra struktura dla produkcyjnego autocomplete.

Zalety:

- kompaktowy indeks,
- szybki lookup,
- dobry do dużych słowników,
- często używany w wyszukiwarkach i bibliotekach searchowych.

Wady:

- bardziej skomplikowana budowa,
- aktualizacje zwykle batchowe,
- wymaga osobnego pipeline’u indeksowania.

---

#### Opcja C: Elasticsearch / OpenSearch / Solr

Można użyć `completion suggester`, edge n-grams albo search-as-you-type.

Zalety:

- szybki start,
- gotowe fuzzy matching,
- łatwy ranking,
- filtrowanie,
- obsługa języków.

Wady:

- większy koszt,
- trudniejsza kontrola ultra-niskiej latencji,
- może być overkillem dla samego query autocomplete,
- operacyjnie cięższe niż specjalizowany indeks w pamięci.

---

### Rekomendacja

Dla dużej skali użyłbym podejścia hybrydowego:

```text
Hot prefixes:
  precomputed top suggestions in memory / Redis

General autocomplete:
  FST / compact trie

Fuzzy + long-tail:
  OpenSearch / specialized fallback

Personalization:
  lightweight reranking layer
```

Nie projektowałbym całego systemu wyłącznie na Elasticsearchu, jeśli latency i koszt są krytyczne.

---

## 9. Normalizacja zapytań

Przed lookupem input powinien być normalizowany:

```text
"  iPhone-15 Pro!! " -> "iphone 15 pro"
```

Typowe kroki:

- lowercase,
- trim spaces,
- Unicode normalization,
- usunięcie lub mapowanie znaków specjalnych,
- obsługa akcentów, np. `łódź` vs `lodz`,
- tokenizacja,
- stemming lub lemmatization — ostrożnie, zależnie od języka,
- transliteracja dla niektórych języków,
- usunięcie niedozwolonych znaków.

Ważne: trzeba przechowywać zarówno wersję normalizowaną do lookupu, jak i wersję displayową.

Przykład:

```json
{
  "normalized": "iphone 15 pro",
  "display": "iPhone 15 Pro"
}
```

---

## 10. Data model sugestii

Przykładowy rekord sugestii:

```json
{
  "suggestion_id": "sug_123",
  "normalized_text": "iphone 15 pro",
  "display_text": "iPhone 15 Pro",
  "type": "query",
  "locale": "en-US",
  "country": "US",
  "popularity_score": 0.92,
  "ctr": 0.18,
  "conversion_rate": 0.07,
  "freshness_score": 0.31,
  "quality_score": 0.88,
  "is_blocked": false,
  "updated_at": "2026-06-05T10:00:00Z"
}
```

Dla entity suggestions:

```json
{
  "suggestion_id": "prod_456",
  "type": "product",
  "entity_id": "product_456",
  "display_text": "iPhone 15 Pro Max",
  "normalized_text": "iphone 15 pro max",
  "image_url": "...",
  "category": "Electronics",
  "availability": "in_stock",
  "popularity_score": 0.97
}
```

---

## 11. Ranking

Najprostszy ranking:

```text
score = popularity
```

Ale w produkcji to zwykle za mało.

Lepszy ranking:

```text
final_score =
  w1 * prefix_match_score +
  w2 * popularity_score +
  w3 * ctr_score +
  w4 * conversion_score +
  w5 * freshness_score +
  w6 * personalization_score +
  w7 * location_score +
  w8 * business_score
  - w9 * risk_score
```

Czynniki:

| Feature         | Znaczenie                                    |
|-----------------|----------------------------------------------|
| Prefix match    | jak dobrze sugestia pasuje do inputu         |
| Popularity      | liczba wyszukiwań                            |
| CTR             | czy użytkownicy klikają po tej sugestii      |
| Conversion      | czy prowadzi do wartościowej akcji           |
| Freshness       | czy temat jest teraz popularny               |
| Personalization | zgodność z użytkownikiem                     |
| Locale          | język i kraj                                 |
| Availability    | np. produkt dostępny w sklepie               |
| Safety          | obniżenie lub usunięcie ryzykownych sugestii |
| Business rules  | kampanie, sezonowość, priorytety             |

---

## 12. Candidate generation vs ranking

Warto rozdzielić dwa etapy.

### 12.1 Candidate generation

Szybko pobiera np. 100–500 potencjalnych sugestii.

Źródła kandydatów:

```text
global popular suggestions
+ locale-specific suggestions
+ trending suggestions
+ personalized suggestions
+ entity suggestions
+ spell-corrected suggestions
```

### 12.2 Ranking

Ranking wybiera top 5–10.

To daje elastyczność: można szybko dodawać nowe źródła kandydatów bez przebudowy całego systemu.

---

## 13. Personalizacja

Personalizacja powinna być lekka, bo autocomplete ma bardzo mały budżet latency.

Możliwe źródła:

- ostatnie wyszukiwania użytkownika,
- ostatnio kliknięte produkty/dokumenty,
- preferowane kategorie,
- lokalizacja,
- język,
- organizacja/workspace,
- historia sesji.

Przykład:

Dwóch użytkowników wpisuje:

```text
jav
```

Programista może dostać:

```text
java
javascript
java stream api
```

Miłośnik kawy:

```text
java coffee
java beans
java island
```

Architektonicznie:

```text
Autocomplete Service
  |
  +--> Global candidates
  +--> User recent searches from fast KV store
  +--> User embedding/profile from Feature Store
  +--> Rerank
```

Nie robiłbym ciężkiego model inference synchronicznie dla każdego requestu, chyba że infrastruktura jest do tego
przygotowana. Lepiej użyć precomputed user features i prostego rerankingu.

---

## 14. Fuzzy matching i literówki

Przykłady:

```text
iphnoe -> iphone
samsng -> samsung
macbok -> macbook
```

Podejścia:

1. **Edit distance**, np. Levenshtein.
2. **BK-tree** dla słownika.
3. **N-gram index**.
4. **Elasticsearch fuzzy query**.
5. **ML spell correction model**.
6. **Keyboard-aware typo model**, np. `o` blisko `p`.

Fuzzy matching jest kosztowniejszy niż prefix lookup, więc warto go uruchamiać tylko gdy:

- nie ma wystarczająco dobrych wyników exact prefix,
- input ma minimum długości, np. 4 znaki,
- użytkownik nie wpisuje bardzo szybko,
- request nie jest z cache.

---

## 15. Trending suggestions

Trending powinno działać w krótkim oknie czasowym.

Przykład:

```text
score_trending =
  searches_last_10min / baseline_searches
```

Źródła:

- query logs,
- click logs,
- social/news trends — zależnie od produktu,
- sezonowość,
- lokalne trendy.

Pipeline:

```text
Kafka
  |
  v
Flink sliding windows
  |
  v
Trending Store
  |
  v
Autocomplete Service
```

Okna:

- 5 minut,
- 30 minut,
- 24 godziny,
- 7 dni.

Trzeba uważać na spam i manipulację. Trending bez anty-abuse łatwo zatruć.

---

## 16. Cache strategy

Autocomplete jest idealnym kandydatem do cache.

### 16.1 Client cache

Cache dla ostatnich prefiksów:

```text
"ip" -> [...]
"iph" -> [...]
"ipho" -> [...]
```

TTL: kilkadziesiąt sekund do kilku minut.

### 16.2 CDN / Edge cache

Dobre dla anonimowych, globalnych sugestii.

Key:

```text
autocomplete:{locale}:{country}:{normalized_prefix}
```

Nie używać dla personalizowanych odpowiedzi bez ostrożności.

### 16.3 Service L1 cache

In-memory cache w instancji serwisu.

### 16.4 Redis / Memcached

Dla gorących prefiksów.

Przykład:

```text
suggestions:en-US:US:iph -> [iphone 15, iphone 15 pro, ...]
```

TTL:

- popularne prefiksy: 5–30 minut,
- trending: 30–120 sekund,
- personalizowane: krótki TTL lub osobny cache per user.

---

## 17. Sharding

Możliwe strategie shardingu:

### 17.1 Shard by prefix

```text
a-f -> shard 1
g-m -> shard 2
n-s -> shard 3
t-z -> shard 4
```

Problem: nierówny rozkład. Prefiksy typu `i`, `s`, `m` mogą być znacznie gorętsze.

### 17.2 Shard by hash prefixu

```text
hash(normalized_prefix) % N
```

Lepszy balans, ale trudniej wykonywać niektóre operacje zakresowe.

### 17.3 Shard by locale + hash

```text
locale -> cluster
hash(prefix) -> shard
```

Dobre przy wielu językach i regionach.

Rekomendacja:

```text
locale/country partitioning + hash-based sharding
```

Do tego osobna obsługa hot keys przez cache i replikację.

---

## 18. Replikacja i dostępność

Każdy shard powinien mieć repliki.

```text
Shard 1 primary
  + replica A
  + replica B
```

Dla odczytów można czytać z replik.

W praktyce:

- autocomplete index może być read-only między publikacjami,
- nowe wersje indeksu można ładować jako immutable snapshot,
- rollout przez blue-green deployment.

Przykład:

```text
index_v41 active
index_v42 building
index_v42 loaded on serving nodes
switch traffic to index_v42
index_v41 kept for rollback
```

---

## 19. Aktualizacja indeksu

Są dwa typy danych.

### 19.1 Batch

Dane historyczne, popularność, jakość, słowniki.

Częstotliwość:

- co godzinę,
- kilka razy dziennie,
- raz dziennie.

### 19.2 Streaming / near-real-time

Dane trendujące, nowe produkty, nowe dokumenty.

Częstotliwość:

- sekundy,
- minuty.

Architektura:

```text
Batch Index:
  Spark job -> full index snapshot

Realtime Delta:
  Kafka/Flink -> small overlay index

Serving:
  query batch index + realtime overlay
```

To jest dobre rozwiązanie, bo pełny FST/trie można budować batchowo, a świeżość uzyskać przez mały overlay.

---

## 20. Indeks: full snapshot + delta overlay

```text
Autocomplete lookup:
  1. Query main index snapshot
  2. Query realtime delta index
  3. Merge candidates
  4. Deduplicate
  5. Rank
  6. Filter
  7. Return top N
```

Zalety:

- szybki main index,
- świeże dane w overlay,
- łatwy rollback,
- mniej ryzykowne aktualizacje.

Delta index można czyścić po kolejnej pełnej przebudowie indeksu.

---

## 21. Deduplication

Trzeba unikać duplikatów:

```text
iphone 15
iPhone 15
iphone-15
iphone15
```

Strategia:

- canonical form,
- normalized text,
- entity id,
- fuzzy deduplication offline,
- reguły display text.

Przykład:

```json
{
  "canonical": "iphone 15",
  "variants": ["iPhone 15", "iphone15", "iphone-15"]
}
```

---

## 22. Safety, policy i abuse

Autocomplete jest widoczne i może generować reputacyjne problemy.

System powinien filtrować:

- spam,
- treści nielegalne,
- treści toksyczne,
- dane osobowe,
- prywatne zapytania innych użytkowników,
- obraźliwe sugestie,
- manipulowane trendy,
- sugestie prowadzące do niedozwolonych wyników.

Warstwy ochrony:

```text
Offline filtering during index build
+ Realtime filtering during serving
+ Manual blocklist/allowlist
+ Abuse detection on query logs
+ Human review for high-risk terms
```

Ważne: nie wolno po prostu wrzucać surowych query logs do sugestii. To klasyczny błąd. Użytkownicy wpisują prywatne,
wrażliwe i przypadkowe rzeczy.

---

## 23. Privacy

Search logs są bardzo wrażliwe.

Zasady:

- nie indeksować zapytań o niskiej częstotliwości,
- stosować minimalny próg popularności, np. query musi wystąpić od wielu użytkowników,
- usuwać PII,
- anonimizować logi,
- ograniczyć retencję,
- nie pokazywać prywatnych sugestii innym użytkownikom,
- personalizację trzymać oddzielnie od globalnego indeksu,
- honorować usunięcie konta / danych użytkownika.

Przykład progu:

```text
Query can enter global suggestions only if:
  unique_users >= 50
  total_searches >= 200
  no PII detected
  no policy violation
```

---

## 24. Observability

Metryki techniczne:

| Metryka             | Cel               |
|---------------------|-------------------|
| p50/p95/p99 latency | kontrola UX       |
| QPS                 | capacity planning |
| cache hit rate      | koszt i latency   |
| error rate          | stabilność        |
| timeout rate        | degradacja        |
| index load time     | deployability     |
| memory usage        | koszt             |
| stale index age     | świeżość          |

Metryki produktowe:

| Metryka                | Znaczenie                      |
|------------------------|--------------------------------|
| suggestion CTR         | czy sugestie są klikane        |
| search completion rate | czy użytkownik kończy search   |
| zero-result rate       | jakość sugestii                |
| reformulation rate     | czy sugestia była zła          |
| conversion rate        | jakość biznesowa               |
| abandonment rate       | czy user rezygnuje             |
| typed characters saved | klasyczna metryka autocomplete |

---

## 25. Logging

Każdy request powinien logować:

```json
{
  "request_id": "req_123",
  "user_id_hash": "u_hash",
  "session_id": "sess_456",
  "input": "iph",
  "normalized_input": "iph",
  "suggestions_shown": ["iphone 15", "iphone 15 pro"],
  "latency_ms": 24,
  "cache_hit": true,
  "locale": "en-US",
  "country": "US",
  "timestamp": "2026-06-05T10:00:00Z"
}
```

Osobno logować eventy:

```text
suggestion_shown
suggestion_clicked
search_submitted
result_clicked
conversion
```

Nie należy trzymać surowych danych dłużej niż potrzeba.

---

## 26. Failure modes

### 26.1 Ranking Service niedostępny

Fallback:

```text
return pre-ranked suggestions from index
```

### 26.2 Personalization Service niedostępny

Fallback:

```text
return global suggestions
```

### 26.3 Redis niedostępny

Fallback:

```text
query local index directly
```

### 26.4 Main index niedostępny

Fallback:

```text
serve stale local snapshot
```

### 26.5 Realtime trending padł

Fallback:

```text
disable trending, use batch index
```

Autocomplete powinien degradować się łagodnie. Lepiej zwrócić mniej spersonalizowane sugestie niż timeout.

---

## 27. Latency budget

Przykładowy budżet server-side:

| Krok                     |  Budżet |
|--------------------------|--------:|
| API Gateway              |  2–5 ms |
| Normalizacja             |    1 ms |
| Cache lookup             |  1–3 ms |
| Index lookup             | 5–15 ms |
| Personalization features | 5–10 ms |
| Ranking                  | 3–10 ms |
| Filtering                |  1–3 ms |
| Serialization            |  1–2 ms |
| Total p95                | < 50 ms |

Jeżeli personalizacja wymaga zdalnych requestów do wielu usług, latency szybko się rozjedzie. Dlatego feature’y
personalizacyjne powinny być lokalne, cache’owane albo precomputed.

---

## 28. ML ranking

Na początku wystarczy ranking heurystyczny.

Później można użyć modelu Learning-to-Rank.

Features:

- prefix length,
- prefix match quality,
- historical CTR,
- historical conversion,
- user category affinity,
- query popularity,
- freshness,
- device,
- time of day,
- locale,
- availability,
- previous session actions.

Model:

- Logistic Regression,
- Gradient Boosted Trees,
- LightGBM/XGBoost,
- mały neural ranker — ostrożnie z latency.

Nie zaczynałbym od ciężkiego modelu neural search dla autocomplete. Najpierw trzeba mieć dobre logi, metryki i
eksperymenty A/B.

---

## 29. Eksperymenty A/B

Autocomplete bardzo wpływa na zachowanie użytkownika, więc zmiany trzeba testować.

Eksperymenty:

- ranking v1 vs v2,
- liczba sugestii,
- debounce time,
- personalizacja on/off,
- trending boost,
- fuzzy correction,
- typy sugestii,
- wygląd UI.

Guardrail metrics:

- latency,
- error rate,
- search success,
- conversion,
- complaint/report rate,
- zero-result rate.

---

## 30. Multi-language support

Problemy:

- różna tokenizacja,
- odmiana słów,
- akcenty,
- transliteracja,
- CJK bez spacji,
- mixed-language queries,
- synonimy,
- lokalne trendy.

Rozwiązanie:

```text
locale-specific normalization
+ locale-specific index
+ language detection
+ fallback to global index
```

Dla języków typu chiński/japoński/koreański trzeba użyć specjalnej tokenizacji, nie prostego split po spacji.

---

## 31. Security

Zabezpieczenia:

- rate limiting,
- bot detection,
- auth-aware personalization,
- brak cache’owania prywatnych danych w publicznym CDN,
- sanitizacja inputu,
- ochrona przed query injection do backendów searchowych,
- limity długości inputu,
- ochrona przed enumeration attacks.

Przykład limitów:

```text
max query length: 100 chars
min prefix length: 2 chars
max limit: 20 suggestions
rate limit anonymous: 10 req/sec/IP
rate limit logged-in: 20 req/sec/user
```

---

## 32. Przykładowy request flow

```text
1. User types "iph"
2. Client waits 100 ms debounce
3. Client sends GET /autocomplete?q=iph
4. API Gateway validates request
5. Autocomplete Service normalizes "iph"
6. Service checks L1 cache
7. If miss, checks Redis
8. If miss, queries Suggestion Index
9. Service fetches lightweight user features
10. Service merges global + personalized + trending candidates
11. Ranking computes final score
12. Safety filter removes blocked suggestions
13. Response is returned
14. Logs are emitted asynchronously
```

---

## 33. Storage choices

| Dane                 | Storage                                       |
|----------------------|-----------------------------------------------|
| Query logs           | Kafka + object storage                        |
| Aggregated stats     | BigQuery/Snowflake/ClickHouse                 |
| Batch processing     | Spark/Beam                                    |
| Stream processing    | Flink/Kafka Streams                           |
| Hot cache            | Redis/Memcached                               |
| Main index           | FST/trie snapshots in object storage + memory |
| Realtime overlay     | Redis/RocksDB/OpenSearch                      |
| User recent searches | KV store, np. DynamoDB/Cassandra/Redis        |
| Feature store        | Feast/Redis/Cassandra/custom                  |
| Abuse/blocklist      | strongly consistent DB + cache                |

---

## 34. Najważniejsze trade-offy

### Trie vs FST vs Search Engine

| Opcja      | Plusy               | Minusy                       |
|------------|---------------------|------------------------------|
| Trie       | szybkie, proste     | duża pamięć                  |
| FST        | szybkie, kompaktowe | trudniejszy build            |
| OpenSearch | gotowe funkcje      | koszt, latency, operacyjność |
| Redis only | prostota            | słabe dla long tail          |
| ML-heavy   | lepszy ranking      | koszt i latency              |

Moja rekomendacja dla dużego systemu:

```text
FST/trie snapshot for serving
+ Redis for hot prefixes
+ realtime overlay
+ optional OpenSearch fallback for fuzzy/long-tail
+ lightweight ranking layer
```

---

## 35. Minimalna wersja MVP

Dla MVP:

```text
Client debounce
+ API service
+ Redis cache
+ OpenSearch completion suggester
+ nightly batch job from search logs
+ basic popularity ranking
+ blocklist
+ metrics
```

To pozwala szybko wystartować.

---

## 36. Wersja produkcyjna high-scale

Dla dużej skali:

```text
Edge cache
+ stateless autocomplete service
+ in-memory FST snapshots
+ Redis hot cache
+ realtime trending overlay
+ personalized reranking
+ policy filter
+ batch + streaming pipelines
+ A/B testing
+ observability
+ abuse prevention
```

---

## 37. Proponowany diagram logiczny

```text
                         ┌────────────────────┐
                         │       Client        │
                         │ debounce + cache    │
                         └─────────┬──────────┘
                                   │
                                   v
                         ┌────────────────────┐
                         │   API Gateway       │
                         │ auth, limits        │
                         └─────────┬──────────┘
                                   │
                                   v
                         ┌────────────────────┐
                         │ Autocomplete Svc    │
                         │ normalize, merge    │
                         └───┬─────────┬──────┘
                             │         │
             ┌───────────────┘         └────────────────┐
             v                                          v
   ┌────────────────────┐                    ┌────────────────────┐
   │ Redis Hot Cache     │                    │ Personalization     │
   │ prefix -> top N     │                    │ user/session feats   │
   └─────────┬──────────┘                    └─────────┬──────────┘
             │                                         │
             v                                         v
   ┌────────────────────┐                    ┌────────────────────┐
   │ Suggestion Index    │                    │ Ranking Service     │
   │ FST / Trie          │                    │ LTR / heuristics    │
   └─────────┬──────────┘                    └─────────┬──────────┘
             │                                         │
             └─────────────────┬───────────────────────┘
                               v
                    ┌────────────────────┐
                    │ Safety Filter       │
                    │ policy, PII, spam   │
                    └─────────┬──────────┘
                              │
                              v
                    ┌────────────────────┐
                    │ Response            │
                    └────────────────────┘
```

Offline pipeline:

```text
Search Events / Click Events / Catalog / Docs
       │
       v
Kafka / Pulsar
       │
       ├───────────────┐
       v               v
Flink Streaming     Spark Batch
Trending stats      Popularity, CTR, quality
       │               │
       v               v
Realtime Overlay    Index Builder
       │               │
       └───────┬───────┘
               v
        Serving Indexes
```

---

## 38. Największe ryzyka projektowe

Najbardziej ryzykowne elementy:

1. **Prywatność query logs** — nie można bezrefleksyjnie pokazywać zapytań użytkowników.
2. **Latency** — personalizacja i fuzzy matching mogą łatwo przekroczyć budżet.
3. **Spam/trending manipulation** — popularne sugestie można próbować zatruwać.
4. **Jakość rankingu** — popularność nie zawsze oznacza trafność.
5. **Multi-language** — prosta tokenizacja szybko się sypie.
6. **Cache invalidation** — szczególnie przy świeżych produktach lub usuniętych treściach.
7. **Hot prefixes** — kilka prefiksów może generować ogromny ruch.
8. **Deduplication** — bez tego UX wygląda tanio i chaotycznie.

---

## 39. Finalna rekomendacja

Najlepszy design dla skalowalnego Search Autocomplete:

```text
Client debounce
+ Edge/CDN cache for anonymous global suggestions
+ Stateless Autocomplete Service
+ L1 in-memory cache
+ Redis hot-prefix cache
+ FST/trie-based serving index
+ realtime delta overlay for fresh/trending data
+ lightweight personalized reranking
+ strict safety/privacy filtering
+ batch + streaming indexing pipeline
+ A/B testing and observability
```

Najważniejsza decyzja architektoniczna: **nie generować sugestii dynamicznie z głównej wyszukiwarki przy każdym
keystroke’u**, tylko maksymalnie dużo prekomputować i serwować z pamięci/cache. Główna wyszukiwarka może być
fallbackiem, ale nie powinna być jedynym mechanizmem dla dużej skali.
