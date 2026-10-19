#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EVENT_ID="${EVENT_ID:-11111111-1111-1111-1111-111111111111}"
USER_ID="${USER_ID:-kubat}"
CORRELATION_ID="smoke-$(date +%s)"

for readiness_url in \
  "$BASE_URL/actuator/health/readiness" \
  "http://localhost:8081/actuator/health/readiness" \
  "http://localhost:8082/actuator/health/readiness" \
  "http://localhost:8083/actuator/health/readiness" \
  "http://localhost:8084/actuator/health/readiness" \
  "http://localhost:8085/actuator/health/readiness"; do
  echo "Waiting for $readiness_url"
  health=""
  for _ in $(seq 1 120); do
    health=$(curl -fsS --connect-timeout 2 --max-time 3 "$readiness_url" 2>/dev/null || true)
    if python -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status") == "UP" else 1)' <<< "$health" 2>/dev/null; then
      break
    fi
    health=""
    sleep 0.5
  done
  if [[ -z "$health" ]]; then
    echo "Readiness failed: $readiness_url" >&2
    curl -sS --connect-timeout 2 --max-time 3 -i "$readiness_url" >&2 || true
    docker compose --profile apps ps --all >&2 || true
    docker compose --profile apps logs --no-color --tail=100 >&2 || true
    exit 1
  fi
done

echo "1/8 GET /events"
curl -fsS "$BASE_URL/events" -H "X-Correlation-Id: $CORRELATION_ID-events" | tee /tmp/events.json >/dev/null

echo "2/8 POST /reservations"
reservation_response=$(curl -fsS -X POST "$BASE_URL/reservations" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: $CORRELATION_ID-reservation" \
  -d "{\"eventId\":\"$EVENT_ID\",\"userId\":\"$USER_ID\",\"quantity\":1}")
echo "$reservation_response"
reservation_id=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$reservation_response")

echo "3/8 POST /orders for reservation $reservation_id"
order_response=$(curl -fsS -X POST "$BASE_URL/orders" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: $CORRELATION_ID-order" \
  -H "Idempotency-Key: smoke-$reservation_id" \
  -d "{\"reservationId\":\"$reservation_id\",\"userId\":\"$USER_ID\"}")
echo "$order_response"
order_id=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$order_response")
order_status=$(python -c 'import json,sys; print(json.load(sys.stdin)["status"])' <<< "$order_response")
test "$order_status" = "PAID"

echo "4/8 retry POST /orders returns the same order"
replay=$(curl -fsS -X POST "$BASE_URL/orders" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: smoke-$reservation_id" \
  -d "{\"reservationId\":\"$reservation_id\",\"userId\":\"$USER_ID\"}")
test "$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$replay")" = "$order_id"

echo "5/8 reservation is CONFIRMED"
reservation=$(curl -fsS "$BASE_URL/reservations/$reservation_id" \
  -H "X-Correlation-Id: $CORRELATION_ID-get-reservation"
)
test "$(python -c 'import json,sys; print(json.load(sys.stdin)["status"])' <<< "$reservation")" = "CONFIRMED"

echo "6/8 outbox was confirmed by RabbitMQ"
for _ in $(seq 1 40); do
  outbox=$(docker compose exec -T order-db psql -U app -d orders -Atc \
    "select id from outbox_messages where aggregate_id='$order_id' and published_at is not null")
  test -n "$outbox" && break
  sleep 0.25
done
test -n "${outbox:-}"

echo "7/8 notification inbox contains the broker messageId"
for _ in $(seq 1 40); do
  inbox=$(docker compose exec -T notification-db psql -U app -d notifications -Atc \
    "select count(*) from processed_messages where message_id='$outbox'")
  test "$inbox" = "1" && break
  sleep 0.25
done
test "$inbox" = "1"

echo "8/8 payment survives process restart"
docker compose restart payment-mock-service >/dev/null
for _ in $(seq 1 40); do
  payment=$(curl -fsS "http://localhost:8084/payments/orders/$order_id" 2>/dev/null || true)
  test -n "$payment" && break
  sleep 0.5
done
test "$(python -c 'import json,sys; print(json.load(sys.stdin)["status"])' <<< "$payment")" = "PAID"
echo "PASS order=$order_id reservation=$reservation_id outbox=$outbox correlation=$CORRELATION_ID"
