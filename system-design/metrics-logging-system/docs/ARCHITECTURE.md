# Architecture Notes

## Phase 1 boundaries

This project intentionally combines several responsibilities in the `telemetry-server` service:

- ingest REST API,
- Kafka producer,
- Kafka consumers,
- ClickHouse writer,
- query API,
- alert evaluator,
- static dashboard.

That is acceptable for Phase 1 because it keeps the system easy to run and review. In Phase 2, split these into separate deployables.

## Data path

```text
Application / Java Agent
  -> POST /api/v1/ingest/logs or /metrics
  -> Kafka logs.raw / metrics.raw
  -> telemetry-server Kafka consumers
  -> ClickHouse MergeTree tables
```

## Query path

```text
Dashboard / Client
  -> GET /api/v1/query/logs
  -> GET /api/v1/query/metrics
  -> ClickHouse SQL
```

## Alert path

```text
AlertRuleStore in memory
  -> scheduled evaluator
  -> ClickHouse metric aggregation
  -> alert_events table
```

## Why ClickHouse in Phase 1

ClickHouse is a strong MVP backend because it handles high-volume append data and analytical queries well. It is not a perfect TSDB and not a perfect full-text log engine, but for Phase 1 it gives the fastest route to a unified backend.

## Why Kafka

Kafka decouples ingestion from persistence. Even in MVP, this prevents ClickHouse latency spikes from directly impacting clients sending telemetry.

## Current limitations

- No exactly-once guarantees.
- No DLQ yet.
- No per-tenant query budget.
- No schema registry.
- Alert rules are in memory; they should move to Postgres or ClickHouse metadata table.
- No authentication layer.

## Phase 2 implementation

This repository now includes the Phase 2 extensions on top of the original MVP:

- **Cold object storage** — `ObjectStorageService` writes compressed NDJSON objects to a local object-storage root. The abstraction is intentionally S3/MinIO-ready; local filesystem is used to keep the demo runnable without cloud credentials.
- **Cold export job** — `ColdExportJob` periodically exports hourly logs and metrics from ClickHouse to `object://...` GZIP objects. Manual export is available via `POST /api/v1/admin/cold-export`.
- **Metric downsampling** — `MetricsDownsampler` builds `metrics_rollup_1m`, `metrics_rollup_5m` and `metrics_rollup_1h`. The query planner selects the rollup table based on requested window and step.
- **Log bloom filters** — `LogBloomFilterService` builds per-tenant/service/level/hour bloom filters from log message tokens. The planner can skip hot scans when a searched term is definitely absent.
- **Query planner** — `QueryPlanner` returns a concrete plan with tier choice, table choice, estimated partitions and optimizations. Plan endpoints are exposed at `/api/v1/query/logs/plan` and `/api/v1/query/metrics/plan`.
- **RBAC** — API-key based authentication with roles `viewer`, `writer`, `admin`. Use `X-API-Key` or `Authorization: Bearer ...`.
- **Quotas** — sliding per-minute quotas for logs, metric samples and query requests; per-tenant max query windows.
- **Alert routing** — alert rules now support routes such as `log` and `webhook`. The default route writes to application logs.

## Phase 3 scaffold

Phase 3 is represented by working extension points rather than a pretend production implementation:

- `/api/v1/phase3/replication/status` — reports the current replication mode and serves as the active-passive/active-active control-plane extension point.
- `/api/v1/phase3/correlate` — correlates a metric spike timestamp with nearby error logs for the same service.
- `/api/v1/phase3/anomaly` — simple z-score based metric anomaly detector.

These are deliberately lightweight. A real Phase 3 would require dedicated replication streams, durable metadata for regional lag, trace ingestion, stronger anomaly models and more advanced authorization boundaries.
