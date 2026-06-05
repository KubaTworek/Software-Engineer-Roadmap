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
