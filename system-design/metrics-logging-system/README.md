# Metrics / Logging System — System Design

Kompleksowy projekt systemu do zbierania, przechowywania, indeksowania, agregowania i udostępniania metryk oraz logów. Architektura jest inspirowana systemami klasy Prometheus, Loki, ELK/OpenSearch, Alertmanager i Grafana, ale opisana jako własny projekt produkcyjny.

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
