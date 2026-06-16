#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT="${TENANT:-demo}"
SERVICE="${SERVICE:-payments}"
API_KEY="${API_KEY:-demo-writer-key}"
NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

curl -sS -X POST "$BASE_URL/api/v1/ingest/logs" \
  -H 'Content-Type: application/json' \
  -H "X-API-Key: $API_KEY" \
  -d "{
    \"tenantId\":\"$TENANT\",
    \"logs\":[{
      \"timestamp\":\"$NOW\",
      \"level\":\"ERROR\",
      \"service\":\"$SERVICE\",
      \"host\":\"demo-host\",
      \"traceId\":\"trace-$RANDOM\",
      \"message\":\"Payment provider timeout from demo script\",
      \"attributes\":{\"source\":\"script\",\"provider\":\"stripe\"}
    }]
  }"

echo
curl -sS -X POST "$BASE_URL/api/v1/ingest/metrics" \
  -H 'Content-Type: application/json' \
  -H "X-API-Key: $API_KEY" \
  -d "{
    \"tenantId\":\"$TENANT\",
    \"series\":[{
      \"name\":\"http_requests_total\",
      \"labels\":{\"service\":\"$SERVICE\",\"status\":\"500\"},
      \"samples\":[{\"timestamp\":\"$NOW\",\"value\":$((RANDOM % 20 + 1))}]
    }]
  }"
echo
