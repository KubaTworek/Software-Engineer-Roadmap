#!/usr/bin/env bash
set -euo pipefail

docker compose exec kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic rate-limit-usage-events --from-beginning
