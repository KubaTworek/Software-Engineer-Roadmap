#!/usr/bin/env bash
set -euo pipefail
BASE=${BASE:-http://localhost:8080}
ADMIN_KEY=${ADMIN_KEY:-demo-admin-key}
TENANT=${TENANT:-demo}

echo "== Phase 3: tenant info =="
curl -sS -H "X-API-Key: $ADMIN_KEY" "$BASE/api/v1/tenants/$TENANT" | jq . || true

echo "== Phase 3: ingest trace =="
curl -sS -X POST "$BASE/api/v1/ingest/traces" \
  -H "X-API-Key: $ADMIN_KEY" \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId":"demo",
    "spans":[
      {"traceId":"trace-phase3-1","spanId":"root","service":"payments","operation":"POST /payments","durationMs":125,"status":"OK","attributes":{"route":"/payments"}},
      {"traceId":"trace-phase3-1","spanId":"db","parentSpanId":"root","service":"payments","operation":"INSERT payment","durationMs":72,"status":"OK","attributes":{"db":"clickhouse"}}
    ]
  }' | jq . || true

echo "== Phase 3: ingest correlated log =="
curl -sS -X POST "$BASE/api/v1/ingest/logs" \
  -H "X-API-Key: $ADMIN_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"demo","logs":[{"level":"ERROR","service":"payments","host":"pod-a","traceId":"trace-phase3-1","message":"provider timeout while authorizing payment","attributes":{"provider":"stripe"}}]}' | jq . || true

echo "== Phase 3: ingest metric =="
curl -sS -X POST "$BASE/api/v1/ingest/metrics" \
  -H "X-API-Key: $ADMIN_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"demo","series":[{"name":"http_latency_ms","labels":{"service":"payments","region":"local"},"samples":[{"value":120},{"value":130},{"value":125},{"value":900}]}]}' | jq . || true

sleep 3

echo "== Phase 3: trace correlation =="
curl -sS -H "X-API-Key: $ADMIN_KEY" "$BASE/api/v1/phase3/correlate/trace/trace-phase3-1?tenantId=demo" | jq . || true

echo "== Phase 3: full-text plan =="
curl -sS -H "X-API-Key: $ADMIN_KEY" "$BASE/api/v1/phase3/fulltext/plan?tenantId=demo&service=payments&level=ERROR&query=provider%20timeout" | jq . || true

echo "== Phase 3: cardinality report =="
curl -sS -H "X-API-Key: $ADMIN_KEY" "$BASE/api/v1/phase3/cardinality/report?tenantId=demo&metricName=http_latency_ms&hours=24" | jq . || true

echo "== Phase 3: replication heartbeat =="
curl -sS -X POST -H "X-API-Key: $ADMIN_KEY" "$BASE/api/v1/phase3/regions/replication/heartbeat?tenantId=demo&targetRegion=eu-west&streamName=logs&lagMs=1500&status=ok&details=demo" || true

echo "\n== Phase 3: replication health =="
curl -sS -H "X-API-Key: $ADMIN_KEY" "$BASE/api/v1/phase3/regions/replication/health?tenantId=demo" | jq . || true
