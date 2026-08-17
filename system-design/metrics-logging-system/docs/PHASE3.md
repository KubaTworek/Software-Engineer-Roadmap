# Phase 3 Implementation

This version extends the Phase 2 MVP into a Phase 3 observability platform prototype. It remains intentionally compact, but the added modules are implemented as real Spring services, repositories, schemas and endpoints rather than only placeholders.

## Implemented capabilities

### Multi-region

Package: `com.example.observability.server.region`

Implemented:

- configurable current region, peer regions and replication mode,
- replication heartbeat endpoint,
- replication health endpoint,
- failover plan endpoint,
- ClickHouse table for replication events.

Endpoints:

```http
GET  /api/v1/phase3/regions/topology
GET  /api/v1/phase3/regions/replication/health?tenantId=demo
POST /api/v1/phase3/regions/replication/heartbeat?tenantId=demo&targetRegion=eu-west&streamName=logs&lagMs=1200&status=ok
GET  /api/v1/phase3/regions/failover-plan?tenantId=demo
```

This is active-passive/active-active ready at the control-plane level. Actual cross-region Kafka/Object Storage replication is represented through heartbeat state and should be connected to MirrorMaker 2, Pulsar geo-replication, cloud-native stream replication, or object-storage replication in a production deployment.

### Advanced cardinality control

Package: `com.example.observability.server.cardinality`

Implemented:

- max labels per series,
- max label value length,
- blocked high-cardinality labels such as `user_id`, `request_id`, `session_id`, `uuid`, `email`, `token`,
- metric series registry,
- hourly label-value accounting,
- cardinality report endpoint.

Endpoint:

```http
GET /api/v1/phase3/cardinality/report?tenantId=demo&metricName=http_requests_total&hours=24
```

The ingest path rejects high-risk metric payloads before they enter Kafka.

### Optional full-text index

Package: `com.example.observability.server.fulltext`

Implemented:

- lightweight token index for logs,
- configurable feature flag: `telemetry.fulltext.enabled`,
- per-hour term buckets,
- query planning endpoint to choose term-index versus fallback scan.

Endpoint:

```http
GET /api/v1/phase3/fulltext/plan?tenantId=demo&service=payments&level=ERROR&query=provider timeout
```

This is intentionally not a full Elasticsearch replacement. It gives a cost-aware optional index that helps the query planner narrow candidate time buckets.

### Anomaly detection

Package: `com.example.observability.server.phase3.AnomalyDetector`

Implemented:

- rolling z-score detection,
- median absolute deviation detection,
- anomaly event persistence,
- anomaly event query endpoint.

Endpoints:

```http
GET /api/v1/phase3/anomaly?tenantId=demo&metricName=http_latency_ms&service=checkout&minutes=360&method=zscore
GET /api/v1/phase3/anomaly?tenantId=demo&metricName=http_latency_ms&service=checkout&minutes=360&method=mad
GET /api/v1/phase3/anomaly/events?tenantId=demo
```

### Correlation: logs, metrics and traces

Implemented:

- trace span ingest endpoint,
- `trace_spans` ClickHouse table,
- correlation by trace ID,
- metric spike to logs/traces correlation over a time window.

Endpoints:

```http
POST /api/v1/ingest/traces
GET  /api/v1/phase3/traces?tenantId=demo&traceId=trace-123
GET  /api/v1/phase3/correlate/trace/trace-123?tenantId=demo
GET  /api/v1/phase3/correlate/metric-logs-traces?tenantId=demo&service=payments&metricName=http_latency_ms&windowSeconds=300
```

### Self-service tenant management

Package: `com.example.observability.server.tenant`

Implemented:

- create/list/get/update tenants,
- create/list API keys,
- dynamically authenticated API keys backed by ClickHouse table `tenant_api_keys`,
- roles: `viewer`, `writer`, `admin`.

Endpoints:

```http
GET   /api/v1/tenants
POST  /api/v1/tenants
GET   /api/v1/tenants/{tenantId}
PATCH /api/v1/tenants/{tenantId}
POST  /api/v1/tenants/{tenantId}/api-keys
GET   /api/v1/tenants/{tenantId}/api-keys
```

Important: created API keys are returned only once in plaintext. The server stores a SHA-256 token hash.

## Demo

Run:

```bash
./scripts/phase3-demo.sh
```

The script demonstrates:

1. Phase 3 tenant creation.
2. Runtime API key creation.
3. Trace ingest.
4. Metric/log/trace correlation.
5. Full-text query planning.
6. Cardinality report.
7. Replication heartbeat and health.
8. Anomaly endpoint.

## Production gaps

This Phase 3 implementation is still a prototype. A production-grade version should add:

- real cross-region stream replication and conflict handling,
- stronger API key hashing with salt/KMS or HMAC,
- migration management through Flyway/Liquibase instead of raw init SQL,
- stricter tenant isolation at query and storage layers,
- distributed anomaly jobs rather than only on-demand detection,
- OpenTelemetry trace protocol support instead of the simplified demo JSON format,
- mature full-text backend for tenants that truly need high-quality text search.
