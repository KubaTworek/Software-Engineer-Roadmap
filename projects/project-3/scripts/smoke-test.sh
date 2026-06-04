#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EVENT_ID="${EVENT_ID:-11111111-1111-1111-1111-111111111111}"
USER_ID="${USER_ID:-kubat}"
CORRELATION_ID="smoke-$(date +%s)"

echo "1/4 GET /events"
curl -fsS "$BASE_URL/events" -H "X-Correlation-Id: $CORRELATION_ID-events" | tee /tmp/events.json >/dev/null

echo "2/4 POST /reservations"
reservation_response=$(curl -fsS -X POST "$BASE_URL/reservations" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: $CORRELATION_ID-reservation" \
  -d "{\"eventId\":\"$EVENT_ID\",\"userId\":\"$USER_ID\",\"quantity\":1}")
echo "$reservation_response"
reservation_id=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$reservation_response")

echo "3/4 POST /orders for reservation $reservation_id"
order_response=$(curl -fsS -X POST "$BASE_URL/orders" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: $CORRELATION_ID-order" \
  -H "Idempotency-Key: smoke-$reservation_id" \
  -d "{\"reservationId\":\"$reservation_id\",\"userId\":\"$USER_ID\"}")
echo "$order_response"

echo "4/4 GET /reservations/$reservation_id"
curl -fsS "$BASE_URL/reservations/$reservation_id" \
  -H "X-Correlation-Id: $CORRELATION_ID-get-reservation"
echo
