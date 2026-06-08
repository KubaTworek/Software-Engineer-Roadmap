#!/usr/bin/env bash
set -euo pipefail

echo "Stopping Redis to test fail-open/fail-closed/local-fallback behavior..."
docker compose stop redis

echo "Send requests now, for example:"
echo "curl -i -H 'X-User-Id: u1' -H 'X-Tenant-Id: acme' -H 'X-Plan: PRO' http://localhost:8080/api/users"
echo
read -p "Press ENTER to restore Redis..."
docker compose start redis
