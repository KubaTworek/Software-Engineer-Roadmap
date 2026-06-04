#!/usr/bin/env bash
set -euo pipefail

CATALOG_URL=${CATALOG_URL:-http://localhost:8081}
PAYMENT_URL=${PAYMENT_URL:-http://localhost:8084}
NOTIFICATION_URL=${NOTIFICATION_URL:-http://localhost:8085}

curl -fsS -X POST "$CATALOG_URL/internal/chaos/catalog/reset" || true
curl -fsS -X POST "$PAYMENT_URL/internal/chaos/payment/reset" || true
curl -fsS -X POST "$NOTIFICATION_URL/internal/chaos/notification/reset" || true

echo "Chaos settings reset."
