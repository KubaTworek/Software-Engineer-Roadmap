package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook;

import java.util.List;

/**
 * Defines checkout-api operational runbooks.
 *
 * These runbooks close the loop between alert detection, diagnosis, mitigation,
 * and verification on the same user-facing metrics that triggered the alert.
 */
public final class CheckoutRunbooks {

    private CheckoutRunbooks() {
    }

    public static Runbook latencySpike() {
        return new Runbook(
                IncidentType.LATENCY_SPIKE,
                "Latency spike",
                List.of(
                        "Alert: CheckoutApiHighRequestLatencyP95",
                        "Dashboard: checkout-api / golden signals",
                        "Signals: p95, p99, error rate, RPS, inflight, DB p95, Redis p95"
                ),
                List.of(
                        new RunbookStep(1, "Narrow the scope by route, region, cluster, and service version."),
                        new RunbookStep(2, "Check whether error rate is also increasing or only latency is affected."),
                        new RunbookStep(3, "Check saturation signals such as CPU, GC, queue depth, and pending requests."),
                        new RunbookStep(4, "Open an exemplar and inspect the slowest trace."),
                        new RunbookStep(5, "If the issue correlates with a deploy, prefer rollback before long root cause analysis.")
                ),
                List.of(
                        "histogram_quantile(0.95, sum by (route, le) (rate(checkout_http_request_duration_seconds_bucket{service=\"checkout-api\"}[5m])))",
                        "sum(rate(checkout_http_requests_total{service=\"checkout-api\",status_code=~\"5..\"}[5m])) / sum(rate(checkout_http_requests_total{service=\"checkout-api\"}[5m]))"
                ),
                List.of(
                        "kubectl top pods -n prod -l app=checkout-api",
                        "kubectl rollout history deploy/checkout-api -n prod",
                        "kubectl logs deploy/checkout-api -n prod --since=10m"
                ),
                List.of(
                        "p95 and p99 returned to an acceptable level",
                        "error rate returned to baseline",
                        "the alert no longer has a symptom-based reason to fire"
                )
        );
    }

    public static Runbook databaseDown() {
        return new Runbook(
                IncidentType.DB_DOWN,
                "Database down",
                List.of(
                        "5xx or timeout spike on DB-dependent routes",
                        "increase in db_client_connection_pending_requests",
                        "increase in db_client_connection_timeouts_total",
                        "traces show a dominant PostgreSQL span"
                ),
                List.of(
                        new RunbookStep(1, "Confirm that this is real user impact, not only a broken exporter."),
                        new RunbookStep(2, "Check reachability and authentication from the application perspective."),
                        new RunbookStep(3, "Check connection pool exhaustion and acquisition timeouts."),
                        new RunbookStep(4, "Limit retries and enable degraded mode or read-only mode if available."),
                        new RunbookStep(5, "After mitigation, verify API p95 and success rate, not only database health.")
                ),
                List.of(
                        "histogram_quantile(0.95, sum by (le, operation) (rate(checkout_db_client_operation_duration_seconds_bucket{service=\"checkout-api\",db_system=\"postgresql\"}[5m])))",
                        "max(db_client_connection_pending_requests{service=\"checkout-api\"})",
                        "sum(rate(db_client_connection_timeouts_total{service=\"checkout-api\"}[5m]))"
                ),
                List.of(
                        "psql \"$DATABASE_URL\" -c 'select 1'",
                        "psql \"$DATABASE_URL\" -c 'select pid, state, wait_event_type, wait_event from pg_stat_activity'",
                        "kubectl get pods -n data -l app=postgres",
                        "kubectl logs statefulset/postgres -n data --since=10m"
                ),
                List.of(
                        "API success rate returned",
                        "API p95 returned to baseline",
                        "pending requests and connection timeouts returned to baseline"
                )
        );
    }

    public static Runbook redisDown() {
        return new Runbook(
                IncidentType.REDIS_DOWN,
                "Redis down",
                List.of(
                        "cache hit ratio drops",
                        "Redis error or timeout rate increases",
                        "DB latency and API latency increase after cache misses"
                ),
                List.of(
                        new RunbookStep(1, "Confirm the impact on checkout-api."),
                        new RunbookStep(2, "Check whether the database is absorbing extra load after cache misses."),
                        new RunbookStep(3, "Check Redis endpoint, authentication, and availability."),
                        new RunbookStep(4, "Enable fallback without causing a cache stampede."),
                        new RunbookStep(5, "Verify hit ratio recovery and database load normalization.")
                ),
                List.of(
                        "sum(rate(checkout_cache_requests_total{service=\"checkout-api\",result=\"hit\"}[5m])) / sum(rate(checkout_cache_requests_total{service=\"checkout-api\"}[5m]))",
                        "histogram_quantile(0.95, sum by (le) (rate(checkout_db_client_operation_duration_seconds_bucket{service=\"checkout-api\",db_system=\"postgresql\"}[5m])))"
                ),
                List.of(
                        "redis-cli -u \"$REDIS_URL\" PING",
                        "redis-cli -u \"$REDIS_URL\" INFO stats",
                        "kubectl get endpoints redis -n prod",
                        "kubectl logs deploy/checkout-api -n prod --since=10m"
                ),
                List.of(
                        "hit ratio returned to baseline",
                        "DB p95 stopped increasing",
                        "API p95 and error rate returned to normal"
                )
        );
    }
}