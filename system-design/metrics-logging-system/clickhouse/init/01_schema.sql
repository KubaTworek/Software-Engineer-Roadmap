CREATE DATABASE IF NOT EXISTS observability;

CREATE TABLE IF NOT EXISTS observability.logs
(
    tenant_id LowCardinality(String),
    timestamp DateTime64(3, 'UTC'),
    ingested_at DateTime64(3, 'UTC') DEFAULT now64(3),
    level LowCardinality(String),
    service LowCardinality(String),
    host String,
    trace_id String,
    message String,
    attributes_json String
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(timestamp)
ORDER BY (tenant_id, service, level, timestamp)
TTL timestamp + INTERVAL 30 DAY DELETE;

CREATE TABLE IF NOT EXISTS observability.logs_bloom_filters
(
    tenant_id LowCardinality(String),
    service LowCardinality(String),
    level LowCardinality(String),
    bucket_start DateTime64(3, 'UTC'),
    bloom_size UInt32,
    hash_count UInt8,
    bloom_bits String,
    created_at DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMMDD(bucket_start)
ORDER BY (tenant_id, service, level, bucket_start)
TTL bucket_start + INTERVAL 30 DAY DELETE;

CREATE TABLE IF NOT EXISTS observability.metrics_samples
(
    tenant_id LowCardinality(String),
    metric_name LowCardinality(String),
    timestamp DateTime64(3, 'UTC'),
    ingested_at DateTime64(3, 'UTC') DEFAULT now64(3),
    value Float64,
    labels_json String,
    service LowCardinality(String) DEFAULT JSONExtractString(labels_json, 'service'),
    host String DEFAULT JSONExtractString(labels_json, 'host')
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(timestamp)
ORDER BY (tenant_id, metric_name, service, timestamp)
TTL timestamp + INTERVAL 30 DAY DELETE;

CREATE TABLE IF NOT EXISTS observability.metrics_rollup_1m
(
    tenant_id LowCardinality(String),
    metric_name LowCardinality(String),
    bucket_start DateTime64(3, 'UTC'),
    labels_json String,
    avg_value Float64,
    min_value Float64,
    max_value Float64,
    sample_count UInt64
)
ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMMDD(bucket_start)
ORDER BY (tenant_id, metric_name, JSONExtractString(labels_json, 'service'), bucket_start, labels_json)
TTL bucket_start + INTERVAL 90 DAY DELETE;

CREATE TABLE IF NOT EXISTS observability.metrics_rollup_5m
(
    tenant_id LowCardinality(String),
    metric_name LowCardinality(String),
    bucket_start DateTime64(3, 'UTC'),
    labels_json String,
    avg_value Float64,
    min_value Float64,
    max_value Float64,
    sample_count UInt64
)
ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMMDD(bucket_start)
ORDER BY (tenant_id, metric_name, JSONExtractString(labels_json, 'service'), bucket_start, labels_json)
TTL bucket_start + INTERVAL 365 DAY DELETE;

CREATE TABLE IF NOT EXISTS observability.metrics_rollup_1h
(
    tenant_id LowCardinality(String),
    metric_name LowCardinality(String),
    bucket_start DateTime64(3, 'UTC'),
    labels_json String,
    avg_value Float64,
    min_value Float64,
    max_value Float64,
    sample_count UInt64
)
ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMM(bucket_start)
ORDER BY (tenant_id, metric_name, JSONExtractString(labels_json, 'service'), bucket_start, labels_json)
TTL bucket_start + INTERVAL 730 DAY DELETE;

CREATE TABLE IF NOT EXISTS observability.alert_events
(
    tenant_id LowCardinality(String),
    rule_id String,
    rule_name String,
    status LowCardinality(String),
    evaluated_at DateTime64(3, 'UTC'),
    observed_value Float64,
    threshold Float64,
    message String
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(evaluated_at)
ORDER BY (tenant_id, rule_id, evaluated_at)
TTL evaluated_at + INTERVAL 30 DAY DELETE;

-- Phase 3: self-service tenants and API keys.
CREATE TABLE IF NOT EXISTS observability.tenants
(
    tenant_id String,
    display_name String,
    status LowCardinality(String),
    plan LowCardinality(String),
    primary_region LowCardinality(String),
    retention_days UInt16,
    created_at DateTime64(3, 'UTC') DEFAULT now64(3),
    updated_at DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY tenant_id;

CREATE TABLE IF NOT EXISTS observability.tenant_api_keys
(
    tenant_id String,
    key_id String,
    token_hash String,
    name String,
    roles Array(String),
    status LowCardinality(String),
    created_at DateTime64(3, 'UTC') DEFAULT now64(3),
    expires_at Nullable(DateTime64(3, 'UTC'))
)
ENGINE = ReplacingMergeTree(created_at)
ORDER BY (tenant_id, key_id);

-- Phase 3: advanced cardinality accounting.
CREATE TABLE IF NOT EXISTS observability.metric_cardinality_hourly
(
    tenant_id LowCardinality(String),
    metric_name LowCardinality(String),
    label_key LowCardinality(String),
    label_value_hash String,
    bucket_start DateTime64(3, 'UTC'),
    examples Array(String),
    created_at DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMMDD(bucket_start)
ORDER BY (tenant_id, metric_name, label_key, label_value_hash, bucket_start)
TTL bucket_start + INTERVAL 30 DAY DELETE;

CREATE TABLE IF NOT EXISTS observability.metric_series_registry
(
    tenant_id LowCardinality(String),
    metric_name LowCardinality(String),
    labels_hash String,
    labels_json String,
    first_seen DateTime64(3, 'UTC'),
    last_seen DateTime64(3, 'UTC'),
    status LowCardinality(String),
    reason String
)
ENGINE = ReplacingMergeTree(last_seen)
ORDER BY (tenant_id, metric_name, labels_hash);

-- Phase 3: optional full-text index. This is intentionally a lightweight token index,
-- not a replacement for Elasticsearch/OpenSearch.
CREATE TABLE IF NOT EXISTS observability.log_fulltext_terms
(
    tenant_id LowCardinality(String),
    service LowCardinality(String),
    level LowCardinality(String),
    bucket_start DateTime64(3, 'UTC'),
    term String,
    doc_count UInt64,
    sample_trace_ids Array(String),
    updated_at DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = SummingMergeTree(doc_count)
PARTITION BY toYYYYMMDD(bucket_start)
ORDER BY (tenant_id, service, level, bucket_start, term)
TTL bucket_start + INTERVAL 30 DAY DELETE;

-- Phase 3: trace spans for log/metric/trace correlation.
CREATE TABLE IF NOT EXISTS observability.trace_spans
(
    tenant_id LowCardinality(String),
    trace_id String,
    span_id String,
    parent_span_id String,
    service LowCardinality(String),
    operation String,
    start_time DateTime64(3, 'UTC'),
    end_time DateTime64(3, 'UTC'),
    duration_ms Float64,
    status LowCardinality(String),
    attributes_json String,
    ingested_at DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(start_time)
ORDER BY (tenant_id, trace_id, service, start_time)
TTL start_time + INTERVAL 30 DAY DELETE;

-- Phase 3: anomaly events and regional replication state.
CREATE TABLE IF NOT EXISTS observability.anomaly_events
(
    tenant_id LowCardinality(String),
    metric_name LowCardinality(String),
    service String,
    detected_at DateTime64(3, 'UTC'),
    method LowCardinality(String),
    score Float64,
    baseline Float64,
    observed Float64,
    severity LowCardinality(String),
    explanation String
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(detected_at)
ORDER BY (tenant_id, metric_name, service, detected_at)
TTL detected_at + INTERVAL 90 DAY DELETE;

CREATE TABLE IF NOT EXISTS observability.region_replication_events
(
    tenant_id LowCardinality(String),
    source_region LowCardinality(String),
    target_region LowCardinality(String),
    stream_name LowCardinality(String),
    event_time DateTime64(3, 'UTC'),
    lag_ms UInt64,
    status LowCardinality(String),
    details String
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(event_time)
ORDER BY (tenant_id, source_region, target_region, stream_name, event_time)
TTL event_time + INTERVAL 30 DAY DELETE;

INSERT INTO observability.tenants (tenant_id, display_name, status, plan, primary_region, retention_days)
SELECT 'demo', 'Demo Tenant', 'active', 'dev', 'local', 30
WHERE NOT EXISTS (SELECT 1 FROM observability.tenants WHERE tenant_id = 'demo');
