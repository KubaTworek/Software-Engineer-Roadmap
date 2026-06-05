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
