#!/usr/bin/env bash
set -euo pipefail

mkdir -p load-tests/reports

BASE_URL=${BASE_URL:-http://localhost:8080}
CATALOG_URL=${CATALOG_URL:-http://localhost:8081}
PAYMENT_URL=${PAYMENT_URL:-http://localhost:8084}
NOTIFICATION_URL=${NOTIFICATION_URL:-http://localhost:8085}

echo "Running k6 load tests against BASE_URL=${BASE_URL}"

run_test() {
  local script="$1"
  echo "\n=== ${script} ==="
  BASE_URL="$BASE_URL" \
  CATALOG_URL="$CATALOG_URL" \
  PAYMENT_URL="$PAYMENT_URL" \
  NOTIFICATION_URL="$NOTIFICATION_URL" \
  k6 run "load-tests/${script}.js"
}

run_test browse-events
run_test create-reservation
run_test create-order
run_test payment-timeout
run_test payment-errors
run_test db-slow
run_test broker-lag
run_test traffic-spike

cat <<MSG

Redis-down scenario is intentionally not included in the automatic run, because it requires stopping Redis externally:
  docker compose stop redis
  k6 run load-tests/redis-down.js
  docker compose start redis
MSG
