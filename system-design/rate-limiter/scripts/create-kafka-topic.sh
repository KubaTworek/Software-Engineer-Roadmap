#!/usr/bin/env bash
set -euo pipefail

docker compose exec kafka kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic rate-limit-usage-events --partitions 3 --replication-factor 1
