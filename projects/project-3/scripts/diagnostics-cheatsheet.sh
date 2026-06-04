#!/usr/bin/env bash
set -euo pipefail

cat <<'TEXT'
Useful local diagnostics:

Prometheus:
  http://localhost:9090

Grafana:
  http://localhost:3000

Tempo:
  http://localhost:3200

RabbitMQ:
  http://localhost:15672

Example metrics:
  histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application))
  sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application, uri)
  resilience4j_circuitbreaker_state
  sum(rate(app_cache_requests_total[5m])) by (cache, result)

Example LogQL:
  {service="order-service"} |= "order_degraded_to_payment_pending"
  {correlationId="<correlation-id>"}
TEXT
