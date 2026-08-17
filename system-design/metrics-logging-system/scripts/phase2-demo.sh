#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-demo-admin-key}"
TENANT="${TENANT:-demo}"

./scripts/send-demo-data.sh
sleep 3

echo "\n--- Logs query plan with bloom filter ---"
curl -sS "$BASE_URL/api/v1/query/logs/plan?tenantId=$TENANT&service=payments&contains=timeout" -H "X-API-Key: $API_KEY" | jq . || true

echo "\n--- Metrics query plan with rollup choice ---"
curl -sS "$BASE_URL/api/v1/query/metrics/plan?tenantId=$TENANT&metricName=http_requests_total&service=payments&minutes=4320&stepSeconds=300" -H "X-API-Key: $API_KEY" | jq . || true

echo "\n--- Quotas ---"
curl -sS "$BASE_URL/api/v1/admin/quotas/$TENANT" -H "X-API-Key: $API_KEY" | jq . || true
