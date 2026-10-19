# Metrics / Logging System — System Design

<!-- reading-guide:start -->
## Ścieżki czytania

| Cel czytania | Punkt wejścia |
| --- | --- |
| Teoria i model problemu | [34. Semantyka sygnałów telemetrycznych](#34-semantyka-sygnałów-telemetrycznych) |
| Implementacja i jej dowody | [Co jest rzeczywiście wykonywalne](#co-jest-rzeczywiście-wykonywalne) |
| Uruchomienie i eksperymenty | [44. Uruchamianie i eksperymenty](#44-uruchamianie-i-eksperymenty) |
| Ograniczenia i scenariusze awarii | [39. Semantyka awarii i dostarczenia](#39-semantyka-awarii-i-dostarczenia) |

Wspólne mechanizmy i ich testy znajdziesz w [indeksie pojęć backend-engineering](../../backend-engineering/README.md#globalny-indeks-pojęć). Tutaj śledź ich konsekwencje dla opisywanego systemu.
Diagramy docelowe i warianty rozwoju czytaj razem z mapą implementacji; sam opis architektury nie oznacza, że wszystkie jej komponenty są uruchamiane w laboratorium.
<!-- reading-guide:end -->

## Zacznij tutaj

| Pytanie | Odpowiedź |
| --- | --- |
| Najważniejszy niezmiennik | Telemetria jednego tenanta nie może być odczytana jako dane innego, a niekontrolowana cardinality nie może wyczerpać systemu. |
| Krytyczny przepływ | Agent/SDK → autoryzacja i walidacja → Kafka → zapis idempotentny w ClickHouse → query/rollup → stanowy alert. |
| Rekomendowany test | `mvnw.cmd clean verify`; kontrakty storage: `mvnw.cmd clean -Pinfrastructure-tests verify`. |
| Świadome ograniczenia | To wykonywalny model pojedynczego regionu. Nie zastępuje Prometheusa, Loki ani Collectora; HA, federacja zapytań i produkcyjny object storage pozostają granicami opisanymi, a nie udawaną implementacją. |

Kompleksowy projekt systemu do zbierania, przechowywania, indeksowania, agregowania i udostępniania metryk oraz logów. Architektura jest inspirowana systemami klasy Prometheus, Loki, ELK/OpenSearch, Alertmanager i Grafana, ale opisana jako własny projekt produkcyjny.

## Co jest rzeczywiście wykonywalne

| Obszar | Implementowana gwarancja | Granica przykładu |
|---|---|---|
| Multi-tenancy | Tenant pochodzi z uwierzytelnionego klucza, a zapisy, odczyty, alerty i cold export są tenant-scoped. | ClickHouse nie ma tu osobnych klastrów ani row policies; izolację wymusza aplikacja i test infrastrukturalny. |
| Ingest | Limit batcha i czasu, redakcja sekretów/PII, quota i walidacja semantyki metryk przed publikacją. | API nie implementuje natywnego OTLP ani remote-write. |
| Kafka | Klucz partycji jest tenantem, producent ma idempotence, consumer używa manualnego commitu, ograniczonego retry i topiców `.DLT`. | Atomowość ClickHouse + offset nie jest transakcją XA; stabilne `event_id` daje efekt idempotentnego redelivery. |
| Storage | Raw dane mają `event_id`; `ReplacingMergeTree` i odczyt `FINAL` usuwają logiczne duplikaty. Bloom filter przechowuje każdy segment i nie może powodować false negative przez nadpisanie batcha. | `FINAL` jest dydaktycznie czytelne, ale w dużym klastrze wymagałoby optymalizacji. |
| Rollup i histogram | Każda rozdzielczość jest liczona z raw: `sum`, `count`, `min`, `max` i ważona średnia pozostają poprawne. Histogram przechowuje skumulowane buckety, sumę, count i exemplar; test dowodzi scalania i estymacji kwantyla. | Schemat nie implementuje exponential histogramu ani kompletnego Prometheus TSDB. |
| Agent | Stabilny identyfikator linii, trwały offset zapisywany dopiero po 2xx, retry z backoffem i jitterem. | Plik źródłowy jest lokalnym buforem; przed rotacją operator musi zapewnić jego retencję. |
| Alerting | Trwałe reguły i stan w PostgreSQL, `for`, jawna polityka braku danych, silence, inhibition, deduplikacja przejść oraz wielowęzłowy outbox z `SKIP LOCKED`, retry i stanem `dead`. | Grouping wielu reguł w jedną wiadomość i rozbudowane matchery etykiet są granicą przykładu. |
| Cold storage | Eksport jest tenant-scoped, atomowo publikuje gzip i manifest z liczbą wierszy oraz SHA-256. Restore drill sprawdza integralność i ładuje dane wyłącznie do izolowanej tabeli. | Adapter używa filesystemu i wczytuje godzinę do pamięci; S3 multipart, katalog danych i restore do produkcyjnych tabel wymagają osobnego workflow. |
| Control plane | Tenanty, hashe kluczy API, wersjonowane reguły, silence i stany alertów znajdują się w PostgreSQL, którego schemat ewoluuje przez Flyway. | ClickHouse pozostaje data plane; nie należy przechowywać w nim silnie spójnych sekretów i konfiguracji. |
| Security | RBAC rozdziela `platform_admin`, admina tenanta, writera i viewera; błędy mają Problem Details, webhook jest fail-closed i wymaga allow-listy HTTPS. | TLS kończy proxy/service mesh. Profil `production` odmawia startu z demo sekretami. |
| Self-observability | Actuator/Prometheus publikuje niskokardynalne liczniki przetworzonych rekordów i commitów offsetu. | Pełne dashboardy i alerty SLO są opisane jako wdrożenie operatorskie. |

Ta tabela jest kontraktem między teorią a kodem. Sekcje poniżej opisują także
architekturę docelową; jeśli mechanizm nie występuje w tabeli jako wykonywalny,
nie należy zakładać, że samo jego opisanie oznacza implementację.

## Semantyka sygnałów: najważniejsze rozróżnienia

Metryka nie jest wyłącznie parą `timestamp + double`. `gauge` opisuje stan,
`counter` jest monotoniczny poza resetem procesu, a histogram przechowuje
skumulowane buckety, sumę i liczność. Z countera wylicza się rate z dwóch
punktów; ujemna różnica oznacza reset, nie ujemny ruch. Histogramy można scalać
tylko przy zgodnych granicach bucketów. Średnia średnich jest błędna, jeśli grupy
mają różne liczności: poprawny rollup przechowuje `sum` i `count`, a następnie
liczy `sum(sum) / sum(count)`.

Timestamp zdarzenia mówi, kiedy obserwacja powstała, a `ingested_at` — kiedy
dotarła do platformy. Partycjonowanie, retencja i zapytania biznesowe korzystają
z event time; ingest time służy do pomiaru laga i deduplikacji. Spóźnione dane
powodują ponowne wyliczenie deterministycznego bucketa, dlatego rollup używa
wersjonowanego `ReplacingMergeTree`, a nie dopisywania średnich do średnich.

Log jest zdarzeniem o ograniczonym schemacie i dowolnych atrybutach. Treść może
zawierać PII lub sekret, więc redakcja następuje przed brokerem — usunięcie
danych dopiero z indeksu pozostawiłoby je w Kafce i cold storage. Bloom filter
jest jedynie negatywnym indeksem: `false` pozwala pominąć segment, `true` nadal
wymaga weryfikacji w źródle prawdy. Utrata choć jednego filtra segmentu mogłaby
dać false negative, dlatego segmenty nie są zastępowane wspólnym kluczem.

Trace ma relację parent–child i propagowany kontekst. Ten projekt przyjmuje
spany i pozwala korelować `trace_id` z logiem, ale nie udaje kompletnego backendu
trace: tail sampling, service graph i OTLP Collector należą do osobnej warstwy.

---

## Spis treści

1. [Cel systemu](#1-cel-systemu)
2. [Założenia skali](#2-założenia-skali)
3. [Wymagania funkcjonalne](#3-wymagania-funkcjonalne)
4. [Wymagania niefunkcjonalne](#4-wymagania-niefunkcjonalne)
5. [High-Level Architecture](#5-high-level-architecture)
6. [Główne komponenty](#6-główne-komponenty)
7. [Projekt metryk](#7-projekt-metryk)
8. [Projekt logów](#8-projekt-logów)
9. [Query system](#9-query-system)
10. [Alerting](#10-alerting)
11. [Multi-tenancy](#11-multi-tenancy)
12. [Security](#12-security)
13. [Retencja i lifecycle danych](#13-retencja-i-lifecycle-danych)
14. [Backpressure i failure handling](#14-backpressure-i-failure-handling)
15. [Idempotency i ordering](#15-idempotency-i-ordering)
16. [Schemat baz danych / stores](#16-schemat-baz-danych--stores)
17. [Query language](#17-query-language)
18. [API surface](#18-api-surface)
19. [Przepływ zapisu metryk](#19-przepływ-zapisu-metryk)
20. [Przepływ zapisu logów](#20-przepływ-zapisu-logów)
21. [Przepływ query logów](#21-przepływ-query-logów)
22. [Cache](#22-cache)
23. [Consistency model](#23-consistency-model)
24. [SLO systemu](#24-slo-systemu)
25. [Deployment](#25-deployment)
26. [Disaster Recovery](#26-disaster-recovery)
27. [Observability samego systemu](#27-observability-samego-systemu)
28. [Najważniejsze trade-offy](#28-najważniejsze-trade-offy)
29. [Potencjalne problemy i mitigacje](#29-potencjalne-problemy-i-mitigacje)
30. [Minimalny MVP](#30-minimalny-mvp)
31. [Proponowany stack technologiczny](#31-proponowany-stack-technologiczny)
32. [Finalna rekomendowana architektura](#32-finalna-rekomendowana-architektura)
33. [Najważniejsze decyzje projektowe](#33-najważniejsze-decyzje-projektowe)
34. [Semantyka sygnałów telemetrycznych](#34-semantyka-sygnałów-telemetrycznych)
35. [Projektowanie pojemności](#35-projektowanie-pojemności)
36. [Jak działa warstwa storage](#36-jak-działa-warstwa-storage)
37. [Teoria wykonywania zapytań](#37-teoria-wykonywania-zapytań)
38. [Alerting jako maszyna stanów](#38-alerting-jako-maszyna-stanów)
39. [Semantyka awarii i dostarczenia](#39-semantyka-awarii-i-dostarczenia)
40. [Jak studiować implementację](#40-jak-studiować-implementację)
41. [Protokoły i interoperacyjność](#41-protokoły-i-interoperacyjność)
42. [HA, DR i ewolucja schematu](#42-ha-dr-i-ewolucja-schematu)
43. [Koszt i zarządzanie pojemnością](#43-koszt-i-zarządzanie-pojemnością)
44. [Uruchamianie i eksperymenty](#44-uruchamianie-i-eksperymenty)

---

# 1. Cel systemu

System ma zbierać, przechowywać, indeksować, agregować i udostępniać:

## 1.1 Metryki

- CPU, memory, latency, error rate, throughput.
- Business metrics, np. liczba płatności, liczba aktywnych użytkowników.
- Dane numeryczne w czasie.

## 1.2 Logi

- Logi aplikacyjne, infrastrukturalne, systemowe.
- Dane tekstowe lub JSON.
- Możliwość wyszukiwania, filtrowania i korelacji z metrykami.

## 1.3 Alerty

- Reguły typu: `error_rate > 5% przez 5 minut`.
- Powiadomienia do Slacka, e-maila, PagerDuty, webhooków.

## 1.4 Dashboardy i zapytania

- Wykresy metryk.
- Przeglądanie logów.
- Korelacja: „wzrosła latencja, pokaż logi z tego okresu”.

---

# 2. Założenia skali

Przyjmujemy produkcyjny, ale nie ekstremalnie gigantyczny system.

| Obszar | Założenie |
|---|---:|
| Liczba serwisów | 500–2000 |
| Liczba hostów / podów | 10 000+ |
| Metryki na sekundę | 1–5 mln samples/s |
| Logi na sekundę | 200k–1 mln events/s |
| Dzienny wolumen logów | 5–50 TB/day |
| Retencja metryk surowych | 15–30 dni |
| Retencja metryk zagregowanych | 6–24 miesiące |
| Retencja logów hot | 7–30 dni |
| Retencja logów cold | 90–365 dni |

Te liczby mocno wpływają na architekturę. System dla 100 GB logów dziennie może być prosty. System dla 50 TB dziennie wymaga shardingu, kompresji, tieringu i bardzo ostrożnego indeksowania.

---

# 3. Wymagania funkcjonalne

## 3.1 Metrics

System powinien umożliwiać:

- push lub pull metryk,
- przyjmowanie próbek typu time series,
- przechowywanie danych z timestampem,
- agregacje: `avg`, `sum`, `min`, `max`, `p50`, `p95`, `p99`,
- query po labelach, np. `service=payments`, `region=eu-west`,
- downsampling starszych danych,
- alerting na podstawie reguł.

## 3.2 Logging

System powinien umożliwiać:

- ingest logów z aplikacji, hostów, kontenerów,
- parsowanie logów JSON i tekstowych,
- wyszukiwanie po czasie, labelach, poziomie logowania i treści,
- agregowanie logów, np. liczba błędów per usługa,
- przechodzenie z metryki do logów w tym samym przedziale czasu,
- retencję hot/cold.

## 3.3 Alerting

System powinien umożliwiać:

- definiowanie reguł alertowych,
- ewaluację reguł cyklicznie,
- deduplikację alertów,
- throttling / rate limiting powiadomień,
- routing alertów do różnych kanałów,
- silence / mute / maintenance windows.

## 3.4 Dashboardy

System powinien umożliwiać:

- wykresy time series,
- tabele logów,
- saved queries,
- dashboardy per service/team,
- RBAC dla danych i dashboardów.

---

# 4. Wymagania niefunkcjonalne

| Wymaganie | Cel |
|---|---|
| Availability | 99.9%–99.99% dla ingestu |
| Durability | bardzo wysoka, szczególnie dla logów audytowych |
| Query latency | metryki: <1–3 s, logi: <5–30 s zależnie od zakresu |
| Write throughput | bardzo wysoki |
| Horizontal scalability | obowiązkowa |
| Backpressure | obowiązkowy |
| Multi-tenancy | zalecane |
| Cost efficiency | krytyczne przy logach |
| Security | TLS, auth, RBAC, audit logs |
| Observability of observability | system musi monitorować sam siebie |

---

# 5. High-Level Architecture

```text
                    ┌────────────────────┐
                    │ Applications/Hosts │
                    └─────────┬──────────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
   ┌──────▼──────┐     ┌──────▼──────┐     ┌──────▼──────┐
   │ Metrics SDK │     │ Logging SDK │     │ Agents       │
   │ / Exporter  │     │ / Appender  │     │ FluentBit etc│
   └──────┬──────┘     └──────┬──────┘     └──────┬──────┘
          │                   │                   │
          └──────────────┬────┴────┬──────────────┘
                         │         │
                  ┌──────▼─────────▼──────┐
                  │ Ingestion Gateway      │
                  │ Auth, validation, rate │
                  │ limiting, batching     │
                  └───────────┬────────────┘
                              │
                    ┌─────────▼─────────┐
                    │ Durable Queue      │
                    │ Kafka/Pulsar       │
                    └──────┬──────┬─────┘
                           │      │
             ┌─────────────▼┐    ┌▼──────────────┐
             │ Metrics Pipe │    │ Logs Pipe      │
             │ Aggregation  │    │ Parse/Index    │
             └──────┬───────┘    └───────┬────────┘
                    │                    │
        ┌───────────▼──────────┐ ┌──────▼─────────────┐
        │ Time Series Storage   │ │ Log Storage         │
        │ TSDB + Object Store   │ │ Object + Index Store│
        └───────────┬──────────┘ └──────┬─────────────┘
                    │                    │
                    └──────────┬─────────┘
                               │
                    ┌──────────▼──────────┐
                    │ Query Service        │
                    │ Metrics + Logs       │
                    └──────────┬──────────┘
                               │
             ┌─────────────────▼─────────────────┐
             │ UI / Dashboard / Alerting Engine   │
             └───────────────────────────────────┘
```

---

# 6. Główne komponenty

## 6.1 SDK / Agent

### Opcja A: SDK w aplikacji

Aplikacja bezpośrednio wysyła metryki/logi.

Zalety:

- łatwa integracja z kontekstem aplikacji,
- możliwość dodania trace ID, user ID, request ID,
- mniejsze opóźnienie.

Wady:

- ryzyko wpływu na aplikację,
- konieczność utrzymywania SDK dla wielu języków,
- problemy przy awarii backendu telemetrycznego.

### Opcja B: Agent lokalny

Aplikacja pisze logi na stdout/file, a agent je zbiera.

Przykłady agentów:

- Fluent Bit,
- Vector,
- OpenTelemetry Collector,
- custom sidecar/daemonset.

Zalety:

- mniejszy wpływ na aplikację,
- lokalny buffering,
- jednolita konfiguracja,
- łatwiejszy rollout.

Wady:

- dodatkowy komponent operacyjny,
- opóźnienie,
- możliwa utrata danych przy złej konfiguracji bufora.

### Rekomendacja

Dla systemu produkcyjnego:

- **metryki**: SDK/exporter + OpenTelemetry Collector,
- **logi**: stdout/file → agent → ingestion gateway,
- **trace/context**: propagować `trace_id`, `span_id`, `request_id`.

---

## 6.2 Ingestion Gateway

To warstwa wejściowa systemu.

### Odpowiedzialności

- TLS termination,
- authentication,
- authorization,
- tenant identification,
- validation payloadów,
- schema enforcement,
- rate limiting,
- sampling,
- batching,
- compression handling,
- backpressure,
- odrzucanie nadmiernie dużych payloadów,
- routing do odpowiednich topiców kolejki.

### API przykładowe

#### Metrics ingest

```http
POST /v1/metrics
Authorization: Bearer <token>
Content-Type: application/json
Content-Encoding: gzip
```

```json
{
  "tenant_id": "team-payments",
  "series": [
    {
      "name": "http_requests_total",
      "labels": {
        "service": "payments",
        "method": "POST",
        "status": "200",
        "region": "eu-central"
      },
      "samples": [
        { "ts": 1730000000000, "value": 123 }
      ]
    }
  ]
}
```

#### Logs ingest

```http
POST /v1/logs
Authorization: Bearer <token>
Content-Type: application/json
Content-Encoding: gzip
```

```json
{
  "tenant_id": "team-payments",
  "logs": [
    {
      "ts": 1730000000000,
      "level": "ERROR",
      "service": "payments",
      "host": "pod-123",
      "trace_id": "abc-123",
      "message": "Payment authorization failed",
      "attributes": {
        "payment_provider": "stripe",
        "error_code": "timeout"
      }
    }
  ]
}
```

---

## 6.3 Durable Queue

Między ingestem a storage powinna być kolejka.

Najczęściej:

- Kafka,
- Pulsar,
- Redpanda,
- Kinesis,
- Pub/Sub.

### Dlaczego kolejka jest potrzebna?

Bez kolejki każdy spike w logach/metriesach od razu uderza w storage. To zwykle kończy się awarią.

Kolejka daje:

- buffering,
- retry,
- replay,
- separację ingestu od storage,
- skalowanie consumerów,
- odporność na chwilowe problemy storage.

### Topic design

```text
metrics-raw-{tenant/shard}
logs-raw-{tenant/shard}
metrics-aggregated-{tenant/shard}
logs-dead-letter
metrics-dead-letter
```

### Partycjonowanie

Dla metryk:

```text
partition_key = hash(tenant_id + metric_name + normalized_labels)
```

Dla logów:

```text
partition_key = hash(tenant_id + service + time_bucket)
```

Trzeba uważać na hot partitions, np. jedna bardzo popularna metryka albo jeden ogromny tenant.

---

# 7. Projekt metryk

## 7.1 Model danych dla metryk

Metryka to time series:

```text
metric_name + labels -> sequence of samples(timestamp, value)
```

Przykład:

```text
http_request_duration_ms{
  service="checkout",
  method="POST",
  region="eu-central",
  status="200"
}
```

Sample:

```text
(timestamp=1730000000000, value=123.45)
```

### Typy metryk

| Typ | Przykład | Uwagi |
|---|---|---|
| Counter | `requests_total` | tylko rośnie |
| Gauge | `memory_usage_bytes` | rośnie i spada |
| Histogram | `request_duration_bucket` | do percentyli |
| Summary | `request_duration_p99` | mniej elastyczne agregacje |

### Problem cardinality

Największy problem w systemach metryk to **wysoka kardynalność labeli**.

Zły przykład:

```text
http_requests_total{user_id="123", request_id="abc", session_id="xyz"}
```

Dobry przykład:

```text
http_requests_total{service="payments", status="500", region="eu"}
```

Label typu `user_id`, `request_id`, `order_id` może wygenerować miliony serii i zabić TSDB.

### Zasady cardinality control

- limit labeli per metryka,
- limit liczby unikalnych wartości labela,
- blokowanie labeli typu `user_id`, `uuid`, `request_id`,
- sampling metryk wysokiej kardynalności,
- per-tenant quota,
- cardinality analyzer,
- automatyczne alerty na eksplozję liczby serii.

---

## 7.2 Metrics ingestion pipeline

```text
Gateway
  -> Kafka metrics-raw
    -> Metrics Validator
      -> Cardinality Controller
        -> Aggregator
          -> TSDB Writer
            -> Hot TSDB
            -> Object Storage
```

### Walidacja

Sprawdzamy:

- czy timestamp nie jest zbyt stary,
- czy timestamp nie jest z przyszłości,
- czy nazwa metryki jest poprawna,
- czy liczba labeli nie przekracza limitu,
- czy payload nie jest zbyt duży,
- czy tenant nie przekracza quota.

### Agregacja

Można agregować:

- per 10 s,
- per 1 min,
- per 5 min,
- per 1 h.

Przykład:

```text
raw samples -> 10s rollup -> 1m rollup -> 5m rollup -> 1h rollup
```

To obniża koszt zapytań dla długich zakresów czasu.

---

## 7.3 Metrics storage

### Hot storage

Do świeżych danych:

- własna TSDB,
- Prometheus-compatible blocks,
- ClickHouse,
- VictoriaMetrics-like design,
- M3DB-like design,
- TimescaleDB dla mniejszej skali.

### Cold storage

Do starszych danych:

- S3 / GCS / Azure Blob,
- Parquet/ORC,
- skompresowane bloki czasowe,
- indeks per blok.

### Proponowany storage layout

```text
s3://metrics/{tenant_id}/{metric_name_hash}/{yyyy}/{mm}/{dd}/{hour}/block.parquet
s3://metrics-index/{tenant_id}/{date}/index-block
```

### Blok TSDB

Blok może zawierać:

```text
block_id
tenant_id
time_range_start
time_range_end
series_id
labels
samples
min_value
max_value
count
checksum
```

### Encoding

Metryki dobrze się kompresują:

- delta-of-delta dla timestampów,
- XOR encoding dla floatów,
- dictionary encoding dla labeli,
- LZ4/ZSTD dla bloków.

---

# 8. Projekt logów

## 8.1 Model danych dla logów

Log event:

```json
{
  "tenant_id": "team-payments",
  "timestamp": 1730000000000,
  "level": "ERROR",
  "service": "payments",
  "host": "pod-123",
  "trace_id": "abc-123",
  "message": "Payment failed due to provider timeout",
  "attributes": {
    "provider": "stripe",
    "error_code": "timeout"
  }
}
```

### Pola podstawowe

| Pole | Opis |
|---|---|
| `tenant_id` | izolacja klienta/teamu |
| `timestamp` | czas zdarzenia |
| `ingested_at` | czas przyjęcia przez system |
| `service` | usługa |
| `level` | DEBUG/INFO/WARN/ERROR |
| `message` | treść |
| `trace_id` | korelacja z tracingiem |
| `attributes` | dodatkowy JSON |
| `source` | agent, host, container, file |

---

## 8.2 Logs ingestion pipeline

```text
Gateway
  -> Kafka logs-raw
    -> Parser
      -> Enricher
        -> Sampler / Filter
          -> Indexer
            -> Log Storage
```

### Parser

Obsługuje:

- JSON logs,
- plaintext logs,
- multiline logs,
- regex parsers,
- timestamp extraction,
- severity extraction.

### Enricher

Dodaje:

- tenant,
- environment,
- region,
- Kubernetes namespace,
- pod name,
- container name,
- deployment version,
- cloud account,
- trace/span IDs, jeżeli dostępne.

### Filtering

Możliwe polityki:

- drop `DEBUG` w produkcji,
- sampling dla powtarzalnych logów,
- redact PII,
- blokowanie sekretów,
- limit rozmiaru message.

---

## 8.3 Logs storage

Logi są znacznie większe od metryk. Wymagają innej strategii.

### Ważna decyzja: pełny indeks czy indeks selektywny?

#### Opcja A: Elasticsearch/OpenSearch-style full index

Zalety:

- szybkie wyszukiwanie full-text,
- elastyczne zapytania,
- dobry UX.

Wady:

- bardzo drogie przy dużym wolumenie,
- koszt indeksowania może być większy niż koszt przechowywania danych,
- trudne zarządzanie retencją i shardami.

#### Opcja B: Loki-style label index + compressed chunks

Zalety:

- dużo tańsze,
- świetne dla logów po service/pod/namespace/time,
- dobra kompresja.

Wady:

- gorsze full-text search,
- query po treści może być wolniejsze,
- trzeba ostrożnie projektować labele.

### Rekomendacja

Dla własnego systemu polecałbym hybrydę:

1. **Indeks strukturalny**:
   - tenant,
   - service,
   - level,
   - environment,
   - region,
   - trace_id,
   - time bucket.
2. **Nie indeksować wszystkiego full-text domyślnie.**
3. **Opcjonalny full-text index tylko dla wybranych pól / tenantów / krótkiej retencji.**

---

## 8.4 Storage layout dla logów

```text
s3://logs/{tenant_id}/{service}/{yyyy}/{mm}/{dd}/{hour}/{chunk_id}.zst
s3://logs-index/{tenant_id}/{yyyy}/{mm}/{dd}/{hour}/index.parquet
```

### Chunk logów

```text
chunk_id
tenant_id
service
level_distribution
time_start
time_end
compressed_payload
bloom_filter_terms
min_timestamp
max_timestamp
line_count
checksum
```

### Indeks

```text
tenant_id
service
level
environment
trace_id
time_bucket
chunk_id
offset_start
offset_end
bloom_filter
```

### Bloom filters

Bloom filter pomaga szybko odrzucać chunki, które na pewno nie zawierają danego termu.

Przykład query:

```text
service="payments" AND message contains "timeout"
```

System:

1. znajduje chunki dla `service=payments`,
2. ogranicza po czasie,
3. sprawdza bloom filter dla słowa `timeout`,
4. pobiera tylko potencjalnie pasujące chunki,
5. dekompresuje i filtruje dokładnie.

---

# 9. Query system

## 9.1 Query API

### Metrics query

```http
GET /v1/query/metrics?query=rate(http_requests_total{service="payments"}[5m])&start=...&end=...&step=60
```

### Logs query

```http
POST /v1/query/logs
```

```json
{
  "tenant_id": "team-payments",
  "start": 1730000000000,
  "end": 1730003600000,
  "filter": {
    "service": "payments",
    "level": "ERROR",
    "contains": "timeout"
  },
  "limit": 1000
}
```

---

## 9.2 Query architecture

```text
UI / API
   │
   ▼
Query Gateway
   │
   ├── Auth/RBAC
   ├── Query validation
   ├── Query planning
   ├── Cache lookup
   │
   ▼
Query Planner
   │
   ├── Metrics Query Engine
   └── Logs Query Engine
          │
          ├── Index lookup
          ├── Chunk selection
          ├── Parallel scan
          ├── Merge/sort
          └── Result pagination
```

### Query Planner

Odpowiada za:

- wybór hot vs cold storage,
- wybór rollupu,
- podział zakresu czasu na shardowane taski,
- pushdown filtrów,
- limitowanie kosztu query,
- timeouty,
- partial results.

### Query limits

Niezbędne są limity:

- maksymalny zakres czasu,
- maksymalna liczba serii,
- maksymalna liczba logów,
- maksymalna liczba równoległych scanów,
- timeout query,
- query cost estimation,
- per-tenant query quota.

Bez tego jeden użytkownik może odpalić query typu „pokaż wszystkie logi z 90 dni” i zdestabilizować cały system.

---

# 10. Alerting

## 10.1 Alerting architecture

```text
Rules Store
    │
    ▼
Alert Scheduler
    │
    ▼
Rule Evaluators
    │
    ├── Metrics Query Engine
    └── Logs Query Engine
    │
    ▼
Alert State Store
    │
    ▼
Notification Router
    │
    ├── Slack
    ├── Email
    ├── PagerDuty
    └── Webhook
```

## 10.2 Alert rule example

```yaml
name: HighPaymentErrorRate
tenant: team-payments
query: |
  rate(http_requests_total{service="payments",status=~"5.."}[5m])
  /
  rate(http_requests_total{service="payments"}[5m])
condition: "> 0.05"
for: 5m
severity: critical
labels:
  team: payments
annotations:
  summary: "High payment error rate"
  runbook: "https://runbooks/company/payments-error-rate"
routes:
  - slack: "#payments-alerts"
  - pagerduty: "payments-critical"
```

## 10.3 Alert lifecycle

```text
OK -> Pending -> Firing -> Resolved
```

### Deduplikacja

Klucz deduplikacji:

```text
hash(tenant_id + alert_name + labels)
```

### Silence

Silence powinien mieć:

```text
tenant_id
matcher labels
start_time
end_time
created_by
reason
```

---

# 11. Multi-tenancy

System powinien być wielotenantowy od początku.

## 11.1 Tenant isolation

Każdy request ma:

```text
tenant_id
user_id
role
quota
```

## 11.2 Izolacja danych

### Shared infrastructure, logical isolation

Najtańsze i najprostsze:

```text
shared Kafka
shared storage
tenant_id in every record
RBAC in query layer
```

Ryzyko:

- bug może ujawnić dane innego tenanta,
- noisy neighbor.

### Dedicated shards per large tenant

Dobre dla dużych klientów/teamów:

```text
tenant A -> shard group A
tenant B -> shard group B
small tenants -> shared pool
```

### Physical isolation

Najdroższe:

```text
separate cluster per tenant
```

Dobre dla:

- regulated industries,
- enterprise customers,
- danych audytowych.

## 11.3 Rekomendacja

Model hybrydowy:

- mali/średni tenanci: shared cluster,
- duzi tenanci: dedicated shard group,
- bardzo wrażliwe dane: dedicated deployment.

---

# 12. Security

## 12.1 Authentication

Możliwości:

- API keys,
- OAuth2/OIDC,
- mTLS dla agentów,
- service tokens.

## 12.2 Authorization

RBAC:

```text
viewer: read dashboards/logs/metrics
editor: create dashboards/queries
admin: manage tokens, retention, alerts
```

ABAC dla danych:

```text
user can read logs where tenant_id=X and team=Y
```

## 12.3 Data protection

Wymagane:

- TLS in transit,
- encryption at rest,
- KMS-managed keys,
- secret redaction,
- PII detection,
- audit logs,
- immutable audit trail dla akcji administracyjnych.

## 12.4 Redaction

Pipeline powinien wykrywać i maskować:

- hasła,
- tokeny,
- klucze API,
- numery kart,
- PESEL / national IDs,
- e-maile, jeżeli polityka tego wymaga.

Przykład:

```text
Authorization: Bearer abc123
```

po redakcji:

```text
Authorization: Bearer [REDACTED]
```

---

# 13. Retencja i lifecycle danych

## 13.1 Metryki

| Dane | Retencja | Storage |
|---|---:|---|
| raw samples | 7–30 dni | hot TSDB |
| 1m rollup | 90 dni | warm/object |
| 5m rollup | 12 miesięcy | object |
| 1h rollup | 24 miesiące+ | object |

## 13.2 Logi

| Dane | Retencja | Storage |
|---|---:|---|
| hot logs | 7–14 dni | fast query storage |
| warm logs | 30–90 dni | object + index |
| cold logs | 180–365 dni | object/archive |
| audit logs | zależnie od compliance | immutable storage |

## 13.3 Lifecycle jobs

```text
raw -> compacted -> downsampled -> archived -> deleted
```

---

# 14. Backpressure i failure handling

To jest jeden z najważniejszych elementów.

## 14.1 Co może pójść źle?

- storage zwalnia,
- Kafka lag rośnie,
- jeden tenant generuje gigantyczny ruch,
- aplikacje emitują za dużo logów,
- query skanuje zbyt dużo danych,
- indeks się opóźnia,
- alert evaluator ma stale timeouty.

## 14.2 Mechanizmy ochronne

### Na agencie

- lokalny disk buffer,
- limit pamięci,
- retry z exponential backoff,
- drop policy,
- sampling.

### Na gatewayu

- rate limit per tenant,
- max request size,
- max labels,
- max log line size,
- circuit breaker,
- early rejection.

### Na kolejce

- partycjonowanie,
- consumer autoscaling,
- lag monitoring,
- DLQ.

### Na storage

- bulk writes,
- idempotency,
- retry-safe writes,
- compaction,
- write-ahead log.

### Na query

- timeout,
- result limit,
- query budget,
- partial results,
- cancellation.

---

# 15. Idempotency i ordering

## 15.1 Metryki

Metryki mogą przyjść:

- z opóźnieniem,
- zdublowane,
- nie po kolei.

Strategia:

```text
series_id + timestamp -> sample
```

Dla duplikatów:

- last write wins,
- albo reject duplicate,
- albo merge w zależności od typu metryki.

## 15.2 Logi

Logi mogą być zduplikowane przy retry.

Można dodać:

```text
event_id = hash(source_id + timestamp + message + sequence_number)
```

Ale pełna deduplikacja logów jest kosztowna. Często robi się dedupe best-effort.

## 15.3 Ordering

Nie gwarantowałbym globalnego orderingu. Wystarczy:

- ordering per source,
- sortowanie po `timestamp` w query,
- dodatkowe `ingested_at` do debugowania opóźnień.

---

# 16. Schemat baz danych / stores

## 16.1 Metadata DB

Relacyjna baza, np. PostgreSQL.

Przechowuje:

```text
tenants
users
api_keys
dashboards
alert_rules
notification_channels
silences
quotas
retention_policies
saved_queries
```

Przykładowe tabele:

```sql
CREATE TABLE tenants (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  plan TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE api_keys (
  id TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  key_hash TEXT NOT NULL,
  scopes JSONB NOT NULL,
  created_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP
);

CREATE TABLE alert_rules (
  id TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  name TEXT NOT NULL,
  query TEXT NOT NULL,
  condition TEXT NOT NULL,
  duration_seconds INT NOT NULL,
  severity TEXT NOT NULL,
  enabled BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE dashboards (
  id TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  name TEXT NOT NULL,
  definition JSONB NOT NULL,
  created_by TEXT,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

---

## 16.2 Series metadata index

Dla metryk:

```text
series_id
tenant_id
metric_name
labels_hash
labels_json
first_seen
last_seen
cardinality_group
```

Przykład:

```sql
CREATE TABLE metric_series (
  series_id BIGINT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  metric_name TEXT NOT NULL,
  labels_hash TEXT NOT NULL,
  labels JSONB NOT NULL,
  first_seen TIMESTAMP NOT NULL,
  last_seen TIMESTAMP NOT NULL
);
```

Przy dużej skali to niekoniecznie będzie klasyczne SQL — może być RocksDB, Cassandra, Bigtable albo własny index.

---

# 17. Query language

## 17.1 Dla metryk

Najbardziej praktyczne: składnia kompatybilna z PromQL albo jej podzbiór.

Przykład:

```text
rate(http_requests_total{service="payments"}[5m])
```

Zalety:

- użytkownicy znają PromQL,
- łatwa migracja,
- gotowe wzorce dashboardów.

Wady:

- PromQL jest złożony,
- implementacja parsera i engine’u nie jest trywialna.

## 17.2 Dla logów

Można użyć składni podobnej do:

```text
{service="payments", level="ERROR"} |= "timeout"
```

albo bardziej SQL-like:

```sql
SELECT *
FROM logs
WHERE service = 'payments'
  AND level = 'ERROR'
  AND message CONTAINS 'timeout'
  AND timestamp BETWEEN now()-1h AND now()
LIMIT 1000;
```

## 17.3 Rekomendacja

- Metryki: PromQL-compatible subset.
- Logi: prosty DSL plus JSON filters.
- Długoterminowo: unified query language do korelacji metryk, logów i trace’ów.

---

# 18. API surface

## 18.1 Ingest APIs

```text
POST /v1/metrics
POST /v1/logs
POST /v1/traces          // opcjonalnie w przyszłości
```

## 18.2 Query APIs

```text
GET  /v1/query/metrics
POST /v1/query/logs
POST /v1/query/correlate
```

## 18.3 Alert APIs

```text
POST   /v1/alerts/rules
GET    /v1/alerts/rules
PUT    /v1/alerts/rules/{id}
DELETE /v1/alerts/rules/{id}

POST /v1/alerts/silences
GET  /v1/alerts/events
```

## 18.4 Dashboard APIs

```text
POST /v1/dashboards
GET  /v1/dashboards/{id}
PUT  /v1/dashboards/{id}
```

## 18.5 Admin APIs

```text
GET /v1/tenants/{id}/usage
PUT /v1/tenants/{id}/quota
PUT /v1/tenants/{id}/retention
```

---

# 19. Przepływ zapisu metryk

```text
1. Service emits metric sample.
2. Agent batches and compresses samples.
3. Gateway authenticates request.
4. Gateway validates tenant quota and payload.
5. Gateway writes to Kafka.
6. Metrics consumer reads from Kafka.
7. Consumer normalizes labels.
8. Consumer checks cardinality limits.
9. Consumer writes samples to TSDB hot storage.
10. Background compactor builds blocks.
11. Blocks are uploaded to object storage.
12. Downsampler creates rollups.
```

---

# 20. Przepływ zapisu logów

```text
1. Application writes log to stdout.
2. Node agent reads container logs.
3. Agent enriches with Kubernetes metadata.
4. Agent batches and compresses logs.
5. Gateway validates and rate-limits.
6. Gateway writes to Kafka.
7. Parser normalizes logs.
8. Redactor removes secrets/PII.
9. Indexer builds structured index and bloom filters.
10. Writer stores compressed chunks.
11. Index metadata is written to index store.
```

---

# 21. Przepływ query logów

```text
1. User queries logs for service=payments, level=ERROR, last 1h.
2. Query Gateway checks auth and quota.
3. Query Planner identifies relevant time buckets.
4. Index service finds candidate chunks.
5. Bloom filters eliminate chunks without matching terms.
6. Workers fetch compressed chunks from storage.
7. Workers decompress and filter exact matches.
8. Results are merged, sorted and paginated.
9. UI displays logs.
```

---

# 22. Cache

Cache jest ważny, ale nie powinien być podstawą poprawności.

## 22.1 Co cache’ować?

- wyniki popularnych dashboard queries,
- metadata labeli,
- series lookup,
- index lookup,
- query plans,
- ostatnie log chunks,
- alert rule evaluation results.

## 22.2 Warstwy cache

```text
Browser cache
Query service memory cache
Redis/Memcached distributed cache
Storage-level cache
```

## 22.3 Cache invalidation

Metryki są append-only, więc cache per time range jest łatwiejszy.

Dla zakresów zakończonych w przeszłości:

```text
cache key = tenant + query + start + end + step
```

Dla `now()` cache TTL powinien być krótki, np. 5–30 sekund.

---

# 23. Consistency model

## 23.1 Ingest

System powinien preferować:

```text
at-least-once ingestion
```

czyli może zdarzyć się duplikat, ale dane nie powinny ginąć.

## 23.2 Query

Query może być eventually consistent.

Przykład:

- log pojawia się w query po 2–10 sekundach,
- metryka pojawia się na dashboardzie po 5–30 sekundach.

## 23.3 Alerting

Alerting nie powinien wymagać sub-second consistency. Typowe okno ewaluacji to 30–60 sekund.

---

# 24. SLO systemu

## 24.1 Ingestion

```text
99.9% poprawnych payloadów przyjętych w < 500 ms
99.99% accepted events nieutraconych
```

## 24.2 Query

```text
95% queries dla ostatniej godziny kończy się w < 3 s
95% queries dla ostatnich 24h kończy się w < 10 s
```

## 24.3 Alerting

```text
99% alertów wykrytych w < 2 min od spełnienia warunku
```

## 24.4 Durability

```text
accepted telemetry durability: 99.999999%
```

---

# 25. Deployment

## 25.1 Kubernetes

Komponenty:

```text
ingestion-gateway
metrics-consumer
logs-parser
logs-indexer
query-gateway
query-workers
alert-scheduler
alert-evaluator
notification-service
metadata-api
ui
```

## 25.2 Autoscaling

Skalowanie według:

- RPS na gatewayach,
- Kafka lag,
- CPU parserów,
- query queue depth,
- liczba aktywnych zapytań,
- liczba reguł alertowych.

## 25.3 Izolacja workloadów

Oddzielne node poole:

```text
ingest nodes
query nodes
storage/compaction nodes
alerting nodes
system nodes
```

Query workloads nie powinny odbierać zasobów ingestowi.

---

# 26. Disaster Recovery

## 26.1 Awaria gatewayów

Rozwiązanie:

- wiele replik,
- load balancer,
- stateless design.

## 26.2 Awaria Kafka brokera

Rozwiązanie:

- replication factor >= 3,
- min.insync.replicas >= 2,
- multi-AZ deployment.

## 26.3 Awaria storage

Rozwiązanie:

- object storage jako durable source,
- replication,
- checksums,
- retry writes,
- compaction replay.

## 26.4 Awaria regionu

Dwie strategie:

### Active-passive

- tańsze,
- prostsze,
- większy RTO.

### Active-active

- droższe,
- trudniejsze,
- lepsza dostępność.

Dla większości firm wystarczy active-passive z replikacją object storage i metadata DB.

---

# 27. Observability samego systemu

Ten system musi monitorować samego siebie.

## 27.1 Kluczowe metryki

### Ingest

```text
ingest_requests_total
ingest_errors_total
ingest_latency_ms
ingested_bytes_total
dropped_events_total
rate_limited_events_total
```

### Kafka

```text
kafka_lag
kafka_produce_errors
kafka_consumer_rebalance_total
```

### Storage

```text
storage_write_latency
storage_write_errors
compaction_duration
object_store_upload_errors
```

### Query

```text
query_latency
query_errors
query_timeout_total
query_scanned_bytes
query_cache_hit_rate
```

### Alerting

```text
alert_evaluation_latency
alert_rules_total
alert_notifications_failed
alert_firing_total
```

---

# 28. Najważniejsze trade-offy

## 28.1 Push vs Pull metrics

| Model | Plusy | Minusy |
|---|---|---|
| Pull | prosty service discovery, Prometheus-style | trudny przez NAT/firewalle, mniej naturalny multi-tenant |
| Push | dobry dla SaaS/multi-cloud, prosty dla agentów | wymaga silnego rate limitingu i auth |

Dla centralnego systemu multi-tenant wybrałbym **push przez agent/collector**.

---

## 28.2 Full-text logs vs selective index

| Model | Plusy | Minusy |
|---|---|---|
| Full-text | szybkie wyszukiwanie | bardzo drogie |
| Selective index | tanie i skalowalne | wolniejsze query po treści |
| Hybryda | praktyczny balans | większa złożoność |

Wybrałbym **hybrydę**.

---

## 28.3 Raw retention vs downsampling

| Model | Plusy | Minusy |
|---|---|---|
| Długa retencja raw | dokładność | koszt |
| Downsampling | niski koszt | utrata szczegółów |
| Tiering | balans | złożoność |

Wybrałbym **raw krótko, rollupy długo**.

---

## 28.4 Kafka required or optional?

Dla małej skali można pisać bezpośrednio do storage. Dla systemu produkcyjnego kolejka jest praktycznie obowiązkowa.

---

# 29. Potencjalne problemy i mitigacje

| Problem | Mitigacja |
|---|---|
| Eksplozja kardynalności metryk | limity labeli, quota, analyzer |
| Zbyt drogie logi | sampling, selective index, tiering |
| Hot tenant | per-tenant shard isolation |
| Wolne query | query planner, cache, rollupy, limity |
| Utrata danych przy spike’u | Kafka, local buffering, backpressure |
| Sekrety w logach | redaction pipeline |
| Jeden user przeciąża system | query budget, rate limits |
| Opóźnione alerty | dedicated evaluator pool |
| Drogi storage | kompresja, lifecycle, object store |
| Zduplikowane dane | idempotency keys, best-effort dedupe |

---

# 30. Minimalny MVP

Nie budowałbym od razu wszystkiego. Sensowny MVP:

## 30.1 Faza 1

- ingest logów i metryk,
- agent lub OpenTelemetry Collector,
- Kafka,
- ClickHouse jako unified backend dla logów i metryk,
- proste query API,
- podstawowy dashboard,
- podstawowy alerting,
- retencja 7–30 dni.

## 30.2 Faza 2

- object storage dla cold data,
- downsampling metryk,
- bloom filters dla logów,
- query planner,
- RBAC,
- alert routing,
- quotas.

## 30.3 Faza 3

- multi-region,
- advanced cardinality control,
- full-text optional index,
- anomaly detection,
- correlation logs/metrics/traces,
- self-service tenant management.

---

# 31. Proponowany stack technologiczny

## 31.1 Praktyczny wariant

| Warstwa | Technologia |
|---|---|
| Agents | OpenTelemetry Collector, Fluent Bit, Vector |
| Ingestion Gateway | Go / Rust / Java |
| Queue | Kafka / Redpanda |
| Stream processing | Flink / Kafka Streams / custom consumers |
| Metrics storage | Mimir/VictoriaMetrics-style TSDB albo ClickHouse |
| Logs hot storage | ClickHouse albo Loki-style chunks |
| Cold storage | S3/GCS + Parquet/ZSTD |
| Metadata DB | PostgreSQL |
| Cache | Redis |
| Query engine | Go/Rust service |
| Alerting | custom evaluator + rules store |
| UI | Grafana-compatible albo własny React frontend |
| Auth | OIDC/OAuth2 |
| Deployment | Kubernetes |

## 31.2 Najbardziej pragmatyczna decyzja

Jeżeli to ma być realny projekt, a nie eksperyment badawczy, użyłbym:

```text
OpenTelemetry Collector
+ Kafka/Redpanda
+ ClickHouse dla hot logs/metrics
+ S3 dla cold storage
+ PostgreSQL dla metadanych
+ Redis dla cache
+ Grafana-compatible API lub własny UI
```

ClickHouse daje bardzo dobry kompromis na start, szczególnie jeśli chcesz szybko mieć query po logach i agregacje metryk. Własna TSDB i własny log index mają sens dopiero przy bardzo dużej skali albo specyficznych wymaganiach kosztowych.

---

# 32. Finalna rekomendowana architektura

```text
                    ┌──────────────────────────┐
                    │ Apps / Hosts / K8s Pods  │
                    └─────────────┬────────────┘
                                  │
                    ┌─────────────▼────────────┐
                    │ OTel Collector / Agent   │
                    │ batching, retry, buffer   │
                    └─────────────┬────────────┘
                                  │
                    ┌─────────────▼────────────┐
                    │ Ingestion Gateway         │
                    │ auth, quota, validation   │
                    └─────────────┬────────────┘
                                  │
                    ┌─────────────▼────────────┐
                    │ Kafka / Redpanda          │
                    │ durable event buffer      │
                    └───────┬───────────┬──────┘
                            │           │
              ┌─────────────▼───┐   ┌──▼──────────────┐
              │ Metrics Pipeline │   │ Logs Pipeline   │
              │ normalize, rollup│   │ parse, redact   │
              └─────────┬───────┘   └───────┬─────────┘
                        │                   │
              ┌─────────▼───────────────────▼─────────┐
              │ Hot Analytical Storage                  │
              │ ClickHouse / TSDB + Index               │
              └─────────┬───────────────────┬─────────┘
                        │                   │
              ┌─────────▼────────┐ ┌────────▼──────────┐
              │ Object Storage    │ │ Metadata DB       │
              │ S3/GCS, Parquet   │ │ Postgres          │
              └─────────┬────────┘ └────────┬──────────┘
                        │                   │
                        └─────────┬─────────┘
                                  │
                    ┌─────────────▼────────────┐
                    │ Query Gateway / Planner  │
                    └─────────────┬────────────┘
                                  │
            ┌─────────────────────▼─────────────────────┐
            │ UI, Dashboards, Alerting, Notifications    │
            └───────────────────────────────────────────┘
```

---

# 33. Najważniejsze decyzje projektowe

Moja rekomendacja końcowa:

1. **Push-based ingestion przez agent/collector.**
2. **Kafka/Redpanda jako obowiązkowa warstwa buforująca.**
3. **Oddzielne pipeline’y dla metryk i logów.**
4. **Metryki przechowywane jako time series z agresywną kontrolą kardynalności.**
5. **Logi przechowywane jako skompresowane chunki + selektywny indeks.**
6. **Cold storage w object storage, nie w drogim full-text engine.**
7. **Retencja warstwowa: hot/warm/cold.**
8. **Query planner z limitami kosztu.**
9. **Alerting jako osobny subsystem.**
10. **Multi-tenancy, quota i RBAC od pierwszej wersji.**
11. **System musi sam siebie monitorować.**

Największe ryzyko projektu to nie sam ingest, tylko **koszt i kontrola kardynalności**. W praktyce systemy metrics/logging najczęściej padają nie dlatego, że nie umieją przyjąć danych, ale dlatego, że użytkownicy emitują zbyt dużo danych o złej strukturze, a query i storage robią się nieprzewidywalnie drogie.

---

## Appendix: skrót decyzji architektonicznych

| Decyzja | Rekomendacja |
|---|---|
| Ingestion model | Push przez agent/collector |
| Buffer | Kafka/Redpanda |
| Metrics storage | TSDB lub ClickHouse na start |
| Logs storage | Chunk-based + selective index |
| Cold storage | S3/GCS + Parquet/ZSTD |
| Query language dla metryk | PromQL-compatible subset |
| Query language dla logów | DSL lub SQL-like |
| Alerting | Osobny subsystem |
| Multi-tenancy | Shared + dedicated shard groups dla dużych tenantów |
| Security | TLS, OIDC/API keys, RBAC/ABAC, redaction |
| Największe ryzyko | Kardynalność i koszt logów |

---

# 34. Semantyka sygnałów telemetrycznych

System telemetryczny nie przechowuje jednego rodzaju danych. Metryka, log i
span mogą odnosić się do tego samego requestu, ale mają inne własności, sposób
agregacji oraz koszt zapytania. Projekt storage'u powinien wynikać z semantyki
sygnału, a nie z wygody jednego DTO.

## 34.1 Counter

Counter reprezentuje skumulowaną liczbę zdarzeń, na przykład:

```text
http_requests_total{service="checkout", status="500"} = 18420
```

Najważniejszym niezmiennikiem countera jest monotoniczność w obrębie życia
procesu. Counter może spaść tylko po restarcie, przejęciu sharda albo błędzie
instrumentacji. Sama wartość `18420` zwykle nie jest interesująca. Query engine
oblicza przyrost lub tempo:

```text
increase = current - previous
rate     = increase / elapsed_seconds
```

Jeżeli `current < previous`, nie wolno zwrócić ujemnego ruchu. Trzeba wykryć
reset i rozpocząć nowe okno. Przy wielu replikach najpierw liczy się rate dla
każdej serii, a dopiero potem sumuje wyniki. Sumowanie surowych counterów przed
obsługą resetów może wygenerować fałszywy spadek.

## 34.2 Gauge

Gauge opisuje stan w danej chwili: temperaturę, długość kolejki, liczbę
aktywnych połączeń albo użycie pamięci. Może rosnąć i maleć. Agregacja zależy od
pytania:

- `avg` pokazuje typowy poziom w oknie;
- `max` pomaga znaleźć pik saturacji;
- `last` odpowiada na pytanie o bieżący stan;
- `sum` ma sens tylko wtedy, gdy serie opisują rozłączne części całości.

Przykładowo suma procentowego użycia CPU wszystkich podów nie jest poprawną
„wartością CPU usługi”, dopóki nie zdefiniujemy mianownika i limitów każdego
poda. Typ metryki nie wystarcza — potrzebna jest jeszcze definicja jednostki.

## 34.3 Histogram i percentyle

Histogram reprezentuje rozkład, na przykład czas odpowiedzi. Klasyczny model
zawiera skumulowane buckety oraz `sum` i `count`:

```text
request_duration_seconds_bucket{le="0.1"}  700
request_duration_seconds_bucket{le="0.5"}  940
request_duration_seconds_bucket{le="1.0"}  990
request_duration_seconds_bucket{le="+Inf"} 1000
request_duration_seconds_sum              182.4
request_duration_seconds_count             1000
```

Bucket `le="0.5"` zawiera również obserwacje z bucketa `le="0.1"`. Histogramy
można agregować między instancjami, jeżeli mają identyczne granice bucketów.
Percentyl jest estymowany z rozkładu i nie jest zwykłą średnią. W szczególności:

```text
p99(A ∪ B) != avg(p99(A), p99(B))
```

Summary obliczające quantile po stronie klienta jest trudniejsze do scalania.
Dlatego system centralny zwykle preferuje histogram klasyczny, exponential
histogram albo szkic taki jak DDSketch/t-digest.

## 34.4 Temporality: cumulative i delta

`cumulative` oznacza wartość od początku życia procesu. `delta` oznacza zmianę
od poprzedniego eksportu. Collector może konwertować te reprezentacje, ale musi
utrzymywać stan i poprawnie obsługiwać restarty oraz brakujące punkty.

| Temporality | Zaleta | Ryzyko |
|---|---|---|
| cumulative | łatwo wykryć retry tego samego punktu i policzyć rate | reset procesu wymaga detekcji |
| delta | prostsza agregacja przyrostów w backendzie | utrata jednego eksportu oznacza utratę przyrostu |

Implementacja przechowuje typ i temporality jawnie na granicy ingestu. Nie są
to dekoracyjne pola — query i przyszły compactor muszą interpretować je zgodnie
z kontraktem.

## 34.5 Log jako zdarzenie

Log jest niezmiennym zdarzeniem, a nie aktualnym stanem. Dobrze zaprojektowany
log ma mały zestaw stabilnych pól indeksowanych oraz elastyczne attributes:

```text
timestamp, severity, service, host, trace_id, message, attributes
```

Wysokokardynalne identyfikatory, takie jak `order_id`, są użyteczne w treści lub
atrybutach, ale kosztowne jako globalny indeks. Selektywny indeks odpowiada
szybko na najczęstsze pytania, a rzadkie wyszukiwanie wykonuje ograniczony scan.
To świadomy kompromis między kosztem zapisu a latencją dowolnego zapytania.

## 34.6 Trace i korelacja

Trace jest grafem spanów opisujących jedną operację rozproszoną. `trace_id`
łączy cały graf, a `span_id` i `parent_span_id` opisują zależności. Poprawna
propagacja kontekstu pozwala przejść:

```text
alert p99 → exemplar → trace → span zależności → log z tym samym trace_id
```

Exemplar jest przykładowym `trace_id` dołączonym do obserwacji histogramu.
Pozwala znaleźć konkretny request bez indeksowania każdego identyfikatora jako
labela metryki. To ważne rozróżnienie: korelacja nie powinna powodować eksplozji
kardynalności.

## 34.7 Event time, ingest time i processing time

System rozróżnia trzy zegary:

- **event time** — kiedy zdarzenie powstało u źródła;
- **ingest time** — kiedy platforma je odebrała;
- **processing time** — kiedy consumer lub job je przetworzył.

Różnica `ingest time - event time` to lag transportu, ale może też zawierać
clock skew. Dane spóźnione powinny trafić do bucketa event time. Job rollupu musi
więc umieć ponownie przeliczyć zamknięty bucket. Z kolei SLO pipeline'u powinno
korzystać także z ingest/processing time, aby odróżnić stare zdarzenie od wolnej
platformy.

---

# 35. Projektowanie pojemności

Diagram architektury bez obliczeń pojemności nie mówi, czy system jest realny.
Estymacje nie muszą być idealne; mają ujawnić rząd wielkości i dominujący koszt.

## 35.1 Wolumen zapisu

Podstawowe przybliżenie:

```text
bytes_per_second = events_per_second × average_encoded_event_size
bytes_per_day    = bytes_per_second × 86 400
stored_bytes     = bytes_per_day × retention_days × replication_factor
                   / compression_ratio
```

Przykład dla 300 000 logów/s, średnio 700 B, retencji hot 14 dni, replikacji 2
i kompresji 5:1:

```text
300 000 × 700 B                  ≈ 210 MB/s
210 MB/s × 86 400                ≈ 18.1 TB/day raw
18.1 TB × 14 × 2 / 5             ≈ 101 TB hot storage
```

Do wyniku trzeba dodać indeksy, metadane części, zapas na merge oraz headroom.
Planowanie klastra dokładnie „na średnią” prowadzi do awarii podczas piku lub
rebalansu.

## 35.2 Metryki i kardynalność

Liczba próbek zależy od liczby aktywnych serii i interwału scrape/export:

```text
samples_per_second = active_series / scrape_interval_seconds
```

Pięć milionów serii eksportowanych co 15 sekund to około 333 tys. próbek/s.
Największym ryzykiem jest iloczyn wartości labeli:

```text
service(100) × endpoint(80) × status(6) × region(4) = 192 000 serii
```

Dodanie `user_id(1 000 000)` nie zwiększa kosztu liniowo o kilka procent, lecz
zmienia przestrzeń kombinacji o sześć rzędów wielkości. Dlatego projekt posiada
zarówno statyczną blokadę ryzykownych labeli, jak i atomowy budżet unikalnych
serii oraz wartości labela na godzinę.

## 35.3 Partycje Kafki

Minimalna liczba partycji wynika z wolniejszej strony pipeline'u:

```text
partitions_by_ingest  = ceil(total_ingest_rate / producer_partition_rate)
partitions_by_consume = ceil(total_ingest_rate / consumer_partition_rate)
partitions            = max(partitions_by_ingest, partitions_by_consume)
```

Należy dodać zapas na awarię brokera i przyszły wzrost. Zbyt mało partycji
ogranicza równoległość, ale tysiące małych partycji zwiększają koszt metadanych,
rebalansu i otwartych plików. Klucz partycji określa granicę orderingu. W tym
laboratorium tenant jest kluczem; duży tenant może wymagać klucza
`tenant + source/shard`, aby uniknąć hot partition.

## 35.4 Batch size a latency

Batching amortyzuje kompresję, request sieciowy i tworzenie części w ClickHouse.
Większy batch poprawia throughput, ale zwiększa:

- czas oczekiwania pierwszego zdarzenia;
- ilość danych ponowionych po błędzie;
- pamięć procesu;
- rozmiar pojedynczego failure domain.

Flush powinien następować po osiągnięciu limitu elementów, bajtów albo czasu —
zależnie od tego, co wystąpi pierwsze. Sam limit liczby rekordów nie chroni przed
batchem zawierającym kilka ogromnych logów.

## 35.5 Bufor i czas na naprawę

Kafka oraz lokalny spool kupują czas operatorowi:

```text
buffer_hours = usable_buffer_bytes / incoming_bytes_per_hour
```

Jeżeli consumer jest wolniejszy od producenta o `50 MB/s`, backlog rośnie o
4.32 TB dziennie. Pytanie operacyjne nie brzmi wyłącznie „czy mamy kolejkę?”,
lecz „ile godzin degradacji storage'u kolejka wytrzyma, zanim zacznie usuwać
najstarsze dane?”.

## 35.6 Little's Law dla zapytań

Dla stabilnego systemu:

```text
concurrency ≈ requests_per_second × average_latency_seconds
```

Przy 200 query/s i średniej 2 s potrzeba obsłużyć około 400 równoległych query.
Jeśli pojedyncze zapytanie może skanować setki GB, brak limitu współbieżności
zamienia niewielki wzrost ruchu w saturację dysku i pamięci. Admission control
powinien uwzględniać koszt, nie tylko liczbę requestów.

---

# 36. Jak działa warstwa storage

## 36.1 Dlaczego ClickHouse

ClickHouse jest kolumnowym silnikiem OLAP. Zapytanie czytające tylko
`timestamp`, `service` i `value` nie musi odczytywać ciężkiej treści loga.
Kompresja działa dobrze, ponieważ sąsiednie wartości tej samej kolumny są
podobne. To odpowiada workloadowi telemetrycznemu: dużo appendów i agregacje po
dużych zakresach, mało aktualizacji pojedynczego rekordu.

ClickHouse nie zastępuje silnie spójnej bazy metadanych. Klucze API, wersje
reguł i stan delivery wymagają constraints, transakcji i jednoznacznej
unikalności, dlatego należą do PostgreSQL.

## 36.2 Parts, partitions i background merge

Insert tworzy immutable part. W tle mniejsze parts są scalane. Partycja określa
granicę zarządzania danymi, nie gwarantuje jednego fizycznego pliku. Zbyt małe
batche produkują „too many parts”, a zbyt drobne partycjonowanie zwiększa koszt
metadanych.

`ORDER BY` w rodzinie MergeTree definiuje sortowanie i sparse primary index.
Nie jest klasycznym unikalnym kluczem. Najczęściej filtrowane prefiksy powinny
znajdować się z lewej strony klucza. Umieszczenie wysokokardynalnego pola na
początku może pogorszyć lokalność danych.

## 36.3 ReplacingMergeTree i idempotencja

`ReplacingMergeTree(version)` zachowuje najnowszą wersję wierszy o tym samym
sorting key, ale deduplikacja następuje podczas asynchronicznego merge. Przed
merge obie wersje fizycznie istnieją. `FINAL` wymusza logiczne scalenie podczas
odczytu, kosztem dodatkowej pracy.

Dlatego poprawny model wygląda następująco:

1. producent nadaje stabilne `event_id`;
2. retry przesyła ten sam identyfikator;
3. sorting key zawiera `event_id`;
4. query wymagające natychmiastowej deduplikacji używa `FINAL` albo równoważnej
   agregacji po kluczu;
5. monitoring mierzy liczbę wersji oczekujących na merge.

Losowe ID generowane dopiero w consumerze nie zapewniłoby idempotencji, bo ten
sam rekord po redelivery dostałby nową tożsamość.

## 36.4 Poprawna algebra rollupu

Rollup musi przechowywać składniki umożliwiające dalsze łączenie:

```text
sum(A ∪ B)   = sum(A) + sum(B)
count(A ∪ B) = count(A) + count(B)
min(A ∪ B)   = min(min(A), min(B))
max(A ∪ B)   = max(max(A), max(B))
avg(A ∪ B)   = (sum(A) + sum(B)) / (count(A) + count(B))
```

Sama średnia nie jest wystarczającym stanem agregatu. Jeśli grupa A zawiera
jedną próbkę `100`, a grupa B dziewięć próbek `0`, średnia średnich daje `50`,
podczas gdy poprawny wynik to `10`. Implementacja liczy każdą rozdzielczość z
raw i przechowuje count, dzięki czemu nie propaguje błędu.

## 36.5 TTL nie usuwa danych natychmiast

TTL w MergeTree jest realizowany podczas background merge. Przekroczenie czasu
retencji nie oznacza natychmiastowego zniknięcia każdego bajtu. System nie może
używać TTL jako mechanizmu ścisłego usunięcia regulacyjnego bez osobnej
procedury i weryfikacji. Wyrażenie TTL musi też zwrócić typ `Date` lub
`DateTime`; przy `DateTime64` potrzebna jest jawna konwersja, którą sprawdza test
wykonujący cały schemat na ClickHouse 24.8.

## 36.6 Bloom filter

Bloom filter odpowiada na pytanie: „czy element na pewno nie występuje?”. Ma
false positives, ale poprawnie zbudowany nie ma false negatives. Dla `n`
elementów, `m` bitów i `k` funkcji hashujących przybliżone prawdopodobieństwo
false positive wynosi:

```text
p ≈ (1 - e^(-k n / m))^k
```

Optymalna liczba hashy jest w przybliżeniu równa `(m/n) × ln(2)`. Filtr jest
indeksem pomocniczym, nigdy źródłem prawdy. Jeżeli istnieje kilka segmentów tej
samej godziny, zapytanie może pominąć godzinę tylko wtedy, gdy każdy segment
mówi „brak”. Zastąpienie starszego filtra nowszym mogłoby stworzyć false
negative na poziomie całego systemu, mimo że pojedynczy filtr jest poprawny.

## 36.7 Hot, warm i cold tier

Warstwy różnią się ceną, latencją i możliwościami query:

| Tier | Typowy format | Cel |
|---|---|---|
| hot | ClickHouse/TSDB | interaktywne query i alerting |
| warm | rzadsze rollupy lub wolniejsze nody | dłuższy trend przy umiarkowanym koszcie |
| cold | Parquet/NDJSON w object storage | audyt, offline analytics, restore |

Eksport nie jest zakończony, dopóki nie ma kompletnego obiektu i manifestu z
liczbą wierszy oraz checksumą. Usunięcie hot danych powinno nastąpić dopiero po
weryfikacji manifestu i możliwości odczytu. Lokalny adapter demonstruje ten
kontrakt; produkcyjnie używa się versioningu, multipart uploadu i lifecycle
policy storage'u.

---

# 37. Teoria wykonywania zapytań

## 37.1 Query planner

Planner zamienia żądanie użytkownika na fizyczny plan:

1. uwierzytelnia tenant i nakłada jego predicate;
2. waliduje zakres czasu oraz limit;
3. wybiera raw albo rollup odpowiedni do szerokości okna i rozdzielczości;
4. stosuje predicate pushdown po czasie, service, severity i labels;
5. konsultuje indeks pomocniczy, np. Bloom filter;
6. szacuje liczbę partycji, bajtów i shardów;
7. odrzuca lub kolejkowuje plan przekraczający budżet;
8. scala częściowe wyniki i stosuje końcowy limit.

Planner nie powinien ufać limitowi odpowiedzi jako limitowi kosztu. Zapytanie
`LIMIT 10` może najpierw przeskanować rok danych, a dopiero potem wybrać dziesięć
wierszy.

## 37.2 Fan-out i partial results

W klastrze query gateway wysyła subquery do wielu shardów. Latencja całości jest
zbliżona do najwolniejszego sharda. Trzeba zdefiniować zachowanie przy błędzie:

- fail closed — brak odpowiedzi, ale brak ryzyka niepełnego wyniku;
- partial result — szybsza użyteczność operacyjna, lecz wynik musi być oznaczony;
- retry wyłącznie brakującego sharda — większa latencja i obciążenie wtórne.

Alerting zazwyczaj powinien być bardziej konserwatywny niż interaktywny
dashboard. Niepełny wynik nie może po cichu wyglądać jak zdrowy system.

## 37.3 Paginacja logów

Duży offset zmusza storage do odnalezienia i odrzucenia poprzednich rekordów.
Stabilniejszy jest cursor/keyset oparty na `(timestamp, event_id)`. Cursor musi
zawierać oba pola, ponieważ wiele zdarzeń ma ten sam timestamp. Sortowanie musi
być deterministyczne również podczas napływu nowych danych.

## 37.4 Cache zapytań

Cache key musi zawierać co najmniej tenant, znormalizowane query, zakres czasu,
rozdzielczość i wersję uprawnień. Brak tenantId w kluczu jest wyciekiem danych,
nie tylko błędem wydajnościowym. Najbezpieczniej cache'ować zamknięte okna czasu;
bieżący bucket szybko się zmienia i wymaga krótkiego TTL lub ominięcia cache.

## 37.5 Ochrona przed kosztownym query

System powinien ograniczać:

- maksymalny zakres czasu;
- liczbę serii i shardów;
- skanowane bajty;
- pamięć agregacji;
- równoległość per tenant;
- czas wykonania;
- liczbę regexów lub ich złożoność;
- maksymalną rozdzielczość odpowiedzi.

Budżet per tenant chroni przed noisy neighbour. Globalny budżet chroni klaster,
gdy wielu poprawnie zachowujących się tenantów generuje pik jednocześnie.

---

# 38. Alerting jako maszyna stanów

## 38.1 Dlaczego samo `value > threshold` nie wystarcza

Jeżeli evaluator wysyła powiadomienie przy każdym przebiegu spełniającym
warunek, operator dostaje alert storm. Potrzebny jest trwały stan reguły:

```text
INACTIVE --condition=true--> PENDING
PENDING  --for elapsed-----> FIRING
PENDING  --condition=false-> INACTIVE
FIRING   --condition=true--> FIRING
FIRING   --condition=false-> RESOLVED
RESOLVED ------------------> INACTIVE/PENDING
```

`for=5m` oznacza ciągłe utrzymywanie warunku przez pięć minut, a nie pięć
dodatnich próbek. Gdy evaluation interval wynosi minutę, brak jednego przebiegu
nie powinien automatycznie zerować stanu bez jawnej polityki missing data.

## 38.2 Missing data

Brak próbki może znaczyć:

- system jest zdrowy i nie było zdarzeń;
- exporter nie działa;
- pipeline telemetryczny ma lag;
- query przekroczyło timeout;
- seria zniknęła po deploymencie.

Reguła musi określić `no data = OK`, `no data = ALERT` albo `KEEP LAST STATE`.
W przeciwnym razie awaria monitoringu może wyglądać jak poprawa monitorowanego
systemu. Dobrym wzorcem jest osobny alert na świeżość danych.

## 38.3 Deduplikacja, grouping i inhibition

- **deduplikacja** zapobiega ponownej notyfikacji tego samego przejścia;
- **grouping** łączy podobne alerty w jedną wiadomość;
- **silence** wycisza dopasowane alerty w określonym czasie;
- **inhibition** wycisza alert podrzędny, gdy aktywna jest przyczyna nadrzędna;
- **throttling** ogranicza częstotliwość przypomnień.

Przykład: aktywny alert `region_down` może inhibować tysiące alertów
`instance_unreachable` z tego regionu. Nie usuwa zdarzeń ani stanu; zmienia
jedynie decyzję o dostarczeniu powiadomienia.

## 38.4 Trwałe dostarczenie

Zmiana stanu i wysłanie webhooka nie mogą być jednym kruchym wywołaniem.
Implementacja zapisuje delivery do PostgreSQL outbox. Workery przejmują rekordy
przez `FOR UPDATE SKIP LOCKED`, dzięki czemu wiele instancji nie wysyła tego
samego rekordu jednocześnie. Błąd powoduje retry z backoffem, a po wyczerpaniu
budżetu stan `dead`, który wymaga obserwowalności i ręcznego replayu.

Status 2xx oznacza zaakceptowanie webhooka, nie wykonanie reakcji po stronie
odbiorcy. Odbiorca również powinien deduplikować po identyfikatorze delivery.

---

# 39. Semantyka awarii i dostarczenia

## 39.1 At-most-once, at-least-once i effectively-once

| Model | Zachowanie | Koszt |
|---|---|---|
| at-most-once | brak duplikatów, ale możliwa utrata | najprostszy |
| at-least-once | brak utraty przy zachowaniu retencji, możliwe duplikaty | retry i deduplikacja |
| exactly-once | pojedynczy efekt w zdefiniowanej granicy transakcji | wysoki, często myląca nazwa |

Kafka nie czyni automatycznie całego przepływu exactly-once. Transakcja brokera
nie obejmuje ClickHouse i zewnętrznego webhooka. Projekt osiąga
**effectively-once dla logical event**: consumer commitnie offset dopiero po
zapisie, a redelivery z tym samym `event_id` jest deduplikowane przez storage.

## 39.2 Okno awarii consumera

Najważniejszy scenariusz:

```text
1. consumer pobiera rekord offset=42
2. zapisuje dane do ClickHouse
3. proces umiera przed commitem offsetu
4. nowa instancja ponownie pobiera offset=42
5. zapisuje ten sam event_id
6. logiczny odczyt widzi jeden rekord
```

Commit przed krokiem 2 tworzyłby okno utraty. Generowanie nowego ID w kroku 5
tworzyłoby trwały duplikat. Dlatego kolejność efektów jest elementem kontraktu.

## 39.3 Poison record i DLT

Rekord niezgodny ze schematem nie naprawi się po setnym retry. Nieskończone
ponawianie blokuje partycję i wszystkie poprawne rekordy za nim. Retry powinno
być ograniczone, a następnie rekord trafia do DLT z:

- oryginalnym topicem, partycją i offsetem;
- klasą oraz komunikatem błędu;
- wersją schematu;
- correlation/event ID;
- czasem i liczbą prób.

DLT nie jest koszem „fire and forget”. Potrzebuje alertu, retencji, narzędzia do
inspekcji i kontrolowanego replayu po poprawieniu przyczyny.

## 39.4 Backpressure zamiast nieograniczonej pamięci

Gdy downstream zwalnia, system może:

1. buforować do jawnego limitu;
2. ograniczyć producenta lub zwrócić 429;
3. zastosować sampling/drop według udokumentowanej polityki;
4. priorytetyzować sygnały audytowe i krytyczne;
5. skalować konsumentów, jeśli bottleneck nie jest dalej w storage'u.

Nieograniczona kolejka w pamięci tylko przesuwa awarię w czasie i kończy się
OOM. Autoscaling consumera nie pomaga, jeżeli wspólny ClickHouse jest już
nasycony — zwiększa wtedy presję na zależność.

## 39.5 Macierz awarii

| Awaria | Oczekiwane zachowanie | Sygnał operatorski |
|---|---|---|
| agent → gateway | retry z jitterem, trwały offset | wiek lokalnego backlogu |
| gateway → Kafka | brak 202 bez ack brokera | publish error rate |
| consumer → ClickHouse | brak commitu, ograniczone retry, DLT | consumer lag i DLT rate |
| PostgreSQL metadata | fail closed dla auth/admin | metadata availability |
| query shard | timeout lub jawny partial result | scanned bytes, failed shards |
| webhook | outbox retry, potem dead | oldest pending delivery |
| cold export | brak usunięcia hot danych bez manifestu | export lag i checksum failures |

---

# 40. Jak studiować implementację

Najlepsza kolejność nie prowadzi po nazwach klas, lecz po granicach gwarancji:

1. **Wejście i zaufanie** — zacznij od `IngestController`, `IngestPolicy`,
   `SensitiveDataRedactor` i RBAC. Sprawdź, które dane klient może podać, a które
   są wyprowadzane z uwierzytelnionego kontekstu.
2. **Koszt metryk** — przejdź przez `CardinalityGuard` i
   `CardinalityBudgetStore`. Zwróć uwagę, dlaczego sam raport po zapisie nie jest
   mechanizmem ochronnym.
3. **Granica kolejki** — porównaj publikację w kontrolerze z manualnym commitem,
   retry i DLT w `TelemetryConsumers` oraz `KafkaTopicConfig`.
4. **Tożsamość danych** — prześledź `eventId` od agenta/ingestu do sorting key w
   ClickHouse i odczytu `FINAL`.
5. **Indeksy pomocnicze** — zobacz, dlaczego Bloom filter i full-text terms mogą
   przyspieszać query, ale nie mogą zastąpić źródła prawdy.
6. **Agregacja** — uruchom `MetricSemanticsTest` i porównaj go z SQL w
   `MetricsDownsampler`.
7. **Alerting** — przejdź od `AlertStateMachine` przez trwały stan do
   `notification_outbox` i workera delivery.
8. **Warstwy danych** — zestaw schemat ClickHouse z migracjami Flyway w
   `telemetry-server/src/main/resources/db/migration`.
   Odpowiedz, dlaczego każda tabela trafiła do konkretnego silnika.
9. **Agent** — zasymuluj odpowiedź 500 i restart. Sprawdź, kiedy checkpoint może
   zostać przesunięty, a kiedy musi pozostać bez zmian.
10. **Kontrakty infrastruktury** — uruchom profil `infrastructure-tests` i
    przeczytaj testy jako wykonywalną dokumentację gwarancji.

Pytania kontrolne po przejściu laboratorium:

- Co stanie się po awarii między zapisem a commitem offsetu?
- Które pola mogą powodować cardinality explosion?
- Dlaczego p99 nie można uśredniać?
- Czy Bloom filter może być podstawą pozytywnej odpowiedzi?
- Jak odróżnić brak danych od zdrowego systemu?
- Która baza jest źródłem prawdy dla reguły alertowej?
- Jak długo system przetrwa niedostępność ClickHouse przy obecnym backlogu?
- Które zapytanie może zwrócić mało danych, ale przeskanować bardzo dużo?
- Co dokładnie oznacza „dostarczono webhook”?
- Jak zweryfikować, że cold export jest kompletny przed usunięciem hot danych?

---

# 41. Protokoły i interoperacyjność

Architektura telemetryczna powinna rozdzielać **model sygnału** od protokołu
transportowego. Ten projekt celowo udostępnia proste JSON/HTTP API, aby pokazać
semantykę, ale platforma produkcyjna zwykle przyjmuje standardowe protokoły:

| Protokół | Typowe zastosowanie | Najważniejsza własność |
|---|---|---|
| OTLP/gRPC | metryki, logi i trace między SDK, Collectorem i backendem | batching, kompresja, streaming HTTP/2 i wspólny model zasobów |
| OTLP/HTTP | środowiska, w których gRPC jest utrudnione | prostszy routing przez proxy kosztem części zalet streamingu |
| Prometheus remote write | próbki time series do długoterminowego backendu | protobuf + snappy, retry i możliwość duplikatów |
| syslog / Fluent Bit | istniejące źródła logów i hosty | niejednorodny schemat, konieczność parsowania i wzbogacenia |

Collector jest warstwą pośrednią, nie bazą danych: może batchować, redagować,
próbkować, routować i ponawiać. Nie usuwa potrzeby quota, idempotencji ani
backpressure w backendzie. Każdy envelope powinien nieść `schema_version`,
resource attributes, event time i stabilne ID. Ewolucja additive jest zwykle
kompatybilna; usunięcie pola lub zmiana jego znaczenia wymaga okresu podwójnego
odczytu/zapisu i obserwacji udziału starych producentów.

Kompresja zmniejsza sieć, ale zwiększa CPU i opóźnienie małego batcha. Limit
dotyczy zarówno rozmiaru skompresowanego, jak i rozpakowanego, aby uniknąć
compression bomb. Odpowiedź 202 potwierdza trwałe przyjęcie przez broker, nie
widoczność w query ani zapis do cold storage.

---

# 42. HA, DR i ewolucja schematu

High availability ogranicza przerwy po awarii komponentu w jednym regionie;
disaster recovery odtwarza usługę po utracie większej domeny awarii. To różne
cele i powinny mieć osobne liczby:

- **RPO** określa dopuszczalną utratę danych, np. 5 minut;
- **RTO** określa maksymalny czas przywrócenia usługi, np. 60 minut;
- Kafka wymaga replikacji partycji i `min.insync.replicas`, ClickHouse —
  replikowanych tabel oraz wielu availability zones;
- PostgreSQL metadata wymaga synchronicznej lub asynchronicznej repliki,
  backupu PITR i testu promocji;
- object storage potrzebuje versioningu, lifecycle i ewentualnej replikacji
  między regionami.

Active-passive upraszcza spójność i koszty, ale wydłuża failover. Active-active
zmniejsza latency i potencjalny RTO, lecz wymaga globalnej tożsamości eventów,
rozwiązywania konfliktów, routingu tenantów oraz jawnej semantyki query podczas
laga replikacji. System nie może obiecać silnego read-after-write, jeśli odczyt
trafia do asynchronicznej repliki.

Backup bez testu odtworzenia jest jedynie nadzieją. Implementowany restore drill
sprawdza tenant-scoped key, SHA-256, NDJSON i liczbę rekordów, a następnie ładuje
rekordy do tabeli `restore_drill_rows` z krótkim TTL. Produkcyjny runbook musi
jeszcze mierzyć RTO, odtworzyć zależności w poprawnej kolejności, porównać count
i zakres czasu oraz uzyskać zatwierdzenie przed przełączeniem query.

Schemat PostgreSQL jest wersjonowany przez Flyway. Migracje są append-only:
opublikowanego pliku `V...sql` nie należy zmieniać, tylko dodać kolejną wersję.
Dla ClickHouse bezpieczny rollout stosuje expand/contract: najpierw nowa kolumna
i czytnik tolerujący oba warianty, potem backfill, przełączenie writerów, pomiar,
a usunięcie starego pola dopiero w późniejszym wdrożeniu.

---

# 43. Koszt i zarządzanie pojemnością

Koszt observability rośnie przede wszystkim z bajtami ingestu, retencją,
kardynalnością serii, replikacją i liczbą skanowanych bajtów. Liczba dashboardów
nie jest dobrym przybliżeniem kosztu: pojedyncze nieograniczone query może być
droższe niż tysiące selektywnych zapytań.

FinOps dla platformy telemetrycznej powinien mierzyć koszt per tenant i sygnał:

- bytes/events/s przed i po kompresji;
- aktywne serie i nowe wartości każdego labela;
- hot-storage bytes, cold-storage bytes i replication factor;
- scanned bytes, CPU-seconds oraz cache hit ratio zapytań;
- udział danych odrzuconych, próbkowanych i wysłanych do DLT.

Mechanizmy kontroli kosztu muszą być przewidywalne: quota, limity okna query,
retencja per klasa danych, sampling oraz preagregacja. Sampling logów nie może
bezrefleksyjnie usuwać audytu i błędów. Z kolei exemplars pozwalają zachować
kilka reprezentatywnych trace ID bez indeksowania każdego request ID jako labela.
Showback informuje tenantów o użyciu; chargeback przypisuje koszt. Oba wymagają
stabilnego modelu atrybucji, nie labela dostarczonego bez zaufania przez klienta.

---

# 44. Uruchamianie i eksperymenty

Najkrótsza ścieżka weryfikacji na Windows:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
.\mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify
docker compose up --build
```

Pierwsza komenda uruchamia szybkie testy bez Dockera. Profil infrastrukturalny
sprawdza PostgreSQL/Flyway, Kafkę, wykonywalność schematu ClickHouse, izolację
tenantów, replay indeksu, histogram oraz pełny przepływ HTTP → Kafka →
ClickHouse → query. Test przekrojowy czeka na eventual consistency zamiast
zakładać, że HTTP 202 oznacza natychmiastowy odczyt.

Po uruchomieniu stacku można wykonać eksperyment wydajnościowy (wymaga `k6`):

```powershell
$env:K6_PROFILE='load' # load, stress, spike albo soak
.\mvnw.cmd --batch-mode --no-transfer-progress -Pload-test integration-test
```

Skrypt używa **open workload model** dla ingestu, więc przeciążenie ujawnia się
jako `dropped_iterations`, a nie jako sztuczne spowolnienie generatora. Mierzy
p95/p99 osobno dla ingestu i query, error rate oraz wymaga zera odrzuconych
iteracji. Przed testem należy zapisać oczekiwaną przepustowość, limit CPU/dysku,
retencję i kryterium przerwania. Load sprawdza cel, stress szuka punktu
nasycenia, spike gwałtowny skok, a soak wycieki i narastające kolejki.

Po eksperymencie porównaj client latency z consumer lag, ClickHouse merge queue,
liczbą parts, scanned bytes, PostgreSQL connection usage i najstarszym rekordem
outboxa. Sam niski czas HTTP 202 nie dowodzi zdrowia systemu, jeśli backlog rośnie
szybciej niż może zostać opróżniony.
