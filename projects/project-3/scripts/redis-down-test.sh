#!/usr/bin/env bash
set -euo pipefail

mkdir -p load-tests/reports

echo "Stopping Redis to verify cache and rate limiter degradation..."
docker compose stop redis
trap 'echo "Restarting Redis..."; docker compose start redis' EXIT

BASE_URL=${BASE_URL:-http://localhost:8080} k6 run load-tests/redis-down.js
