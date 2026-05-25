package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts;

/**
 * Defines checkout-api alerting rules.
 *
 * These alerts are symptom-based: they focus on user-visible latency and errors,
 * not on every possible technical cause.
 */
public final class CheckoutApiAlertRules {

    private CheckoutApiAlertRules() {
    }

    public static PrometheusAlertRule highRequestLatencyP95() {
        String expression = """
                histogram_quantile(
                  0.95,
                  sum by (service, route, le) (
                    rate(checkout_http_request_duration_seconds_bucket{
                      service="checkout-api",
                      status_code!~"5.."
                    }[5m])
                  )
                ) > 0.75
                and
                sum by (service, route) (
                  rate(checkout_http_requests_total{service="checkout-api"}[5m])
                ) > 1
                """;

        AlertLabels labels = AlertLabels.builder()
                .severity(AlertSeverity.PAGE)
                .priority(AlertPriority.P1)
                .team("backend")
                .service("checkout-api")
                .cluster("prod-eu-central")
                .environment("production")
                .build();

        AlertAnnotations annotations = AlertAnnotations.builder()
                .summary("High p95 latency for {{ $labels.route }}")
                .description("P95 exceeds 750 ms for 10 minutes while traffic is significant.")
                .runbookUrl("https://wiki.example.internal/runbooks/latency-spike")
                .dashboardUrl("https://grafana.example.internal/d/checkout-api-golden-signals")
                .build();

        return new PrometheusAlertRule(
                "CheckoutApiHighRequestLatencyP95",
                expression,
                "10m",
                "5m",
                labels,
                annotations
        );
    }

    public static PrometheusAlertRule highErrorRate5xx() {
        String expression = """
                (
                  sum by (service, route) (
                    rate(checkout_http_requests_total{
                      service="checkout-api",
                      status_code=~"5.."
                    }[5m])
                  )
                  /
                  sum by (service, route) (
                    rate(checkout_http_requests_total{
                      service="checkout-api"
                    }[5m])
                  )
                ) > 0.05
                and
                sum by (service, route) (
                  rate(checkout_http_requests_total{service="checkout-api"}[5m])
                ) > 1
                """;

        AlertLabels labels = AlertLabels.builder()
                .severity(AlertSeverity.PAGE)
                .priority(AlertPriority.P1)
                .team("backend")
                .service("checkout-api")
                .cluster("prod-eu-central")
                .environment("production")
                .build();

        AlertAnnotations annotations = AlertAnnotations.builder()
                .summary("High 5xx error rate for {{ $labels.route }}")
                .description("5xx error ratio exceeds 5% for 10 minutes while traffic is significant.")
                .runbookUrl("https://wiki.example.internal/runbooks/latency-spike")
                .dashboardUrl("https://grafana.example.internal/d/checkout-api-golden-signals")
                .build();

        return new PrometheusAlertRule(
                "CheckoutApiHigh5xxErrorRate",
                expression,
                "10m",
                "5m",
                labels,
                annotations
        );
    }

    public static PrometheusAlertRule databaseConnectionTimeouts() {
        String expression = """
                sum by (service, pool) (
                  rate(db_client_connection_timeouts_total{
                    service="checkout-api"
                  }[5m])
                ) > 0
                """;

        AlertLabels labels = AlertLabels.builder()
                .severity(AlertSeverity.PAGE)
                .priority(AlertPriority.P1)
                .team("backend")
                .service("checkout-api")
                .cluster("prod-eu-central")
                .environment("production")
                .build();

        AlertAnnotations annotations = AlertAnnotations.builder()
                .summary("Database connection timeouts in {{ $labels.service }}")
                .description("The application is timing out while acquiring database connections.")
                .runbookUrl("https://wiki.example.internal/runbooks/db-down")
                .dashboardUrl("https://grafana.example.internal/d/checkout-api-db")
                .build();

        return new PrometheusAlertRule(
                "CheckoutApiDatabaseConnectionTimeouts",
                expression,
                "5m",
                "5m",
                labels,
                annotations
        );
    }

    public static PrometheusAlertRule redisErrorsOrTimeouts() {
        String expression = """
                sum by (service, operation) (
                  rate(checkout_cache_requests_total{
                    service="checkout-api",
                    result=~"error|timeout"
                  }[5m])
                ) > 0
                """;

        AlertLabels labels = AlertLabels.builder()
                .severity(AlertSeverity.WARNING)
                .priority(AlertPriority.P2)
                .team("backend")
                .service("checkout-api")
                .cluster("prod-eu-central")
                .environment("production")
                .build();

        AlertAnnotations annotations = AlertAnnotations.builder()
                .summary("Redis errors or timeouts in {{ $labels.service }}")
                .description("Cache operations are failing or timing out. Check whether this is increasing DB and API latency.")
                .runbookUrl("https://wiki.example.internal/runbooks/redis-down")
                .dashboardUrl("https://grafana.example.internal/d/checkout-api-cache")
                .build();

        return new PrometheusAlertRule(
                "CheckoutApiRedisErrorsOrTimeouts",
                expression,
                "5m",
                "5m",
                labels,
                annotations
        );
    }
}