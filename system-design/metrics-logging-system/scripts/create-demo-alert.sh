#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT="${TENANT:-demo}"
SERVICE="${SERVICE:-payments}"
API_KEY="${API_KEY:-demo-admin-key}"

curl -sS -X POST "$BASE_URL/api/v1/alerts/rules" \
  -H 'Content-Type: application/json' \
  -H "X-API-Key: $API_KEY" \
  -d "{
    \"tenantId\":\"$TENANT\",
    \"name\":\"High 5xx request count\",
    \"metricName\":\"http_requests_total\",
    \"labelFilters\":{\"service\":\"$SERVICE\",\"status\":\"500\"},
    \"aggregation\":\"sum\",
    \"operator\":\">\",
    \"threshold\":10,
    \"windowSeconds\":300,
    \"severity\":\"critical\",
    \"routes\":[{\"type\":\"log\",\"target\":\"console\"}],
    \"enabled\":true
  }"
echo
