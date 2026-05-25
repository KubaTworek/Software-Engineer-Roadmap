package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

/**
 * Stores canonical PromQL queries used by dashboards and runbooks.
 *
 * In a real system these queries often live in Grafana dashboards,
 * alerting rules, or runbook documentation rather than Java code.
 */
public final class PromqlQueries {

    private PromqlQueries() {
    }

    public static final String HTTP_RPS_BY_ROUTE = """
            sum by (route) (
              rate(checkout_http_requests_total{service="checkout-api"}[5m])
            )
            """;

    public static final String HTTP_5XX_ERROR_RATIO_BY_ROUTE = """
            sum by (route) (
              rate(checkout_http_requests_total{
                service="checkout-api",
                status_code=~"5.."
              }[5m])
            )
            /
            sum by (route) (
              rate(checkout_http_requests_total{service="checkout-api"}[5m])
            )
            """;

    public static final String HTTP_P95_SUCCESS_LATENCY_BY_ROUTE = """
            histogram_quantile(
              0.95,
              sum by (route, le) (
                rate(checkout_http_request_duration_seconds_bucket{
                  service="checkout-api",
                  status_code!~"5.."
                }[5m])
              )
            )
            """;

    public static final String HTTP_P99_SUCCESS_LATENCY_BY_ROUTE = """
            histogram_quantile(
              0.99,
              sum by (route, le) (
                rate(checkout_http_request_duration_seconds_bucket{
                  service="checkout-api",
                  status_code!~"5.."
                }[5m])
              )
            )
            """;

    public static final String CACHE_HIT_RATIO = """
            sum(rate(checkout_cache_requests_total{
              service="checkout-api",
              result="hit"
            }[5m]))
            /
            sum(rate(checkout_cache_requests_total{
              service="checkout-api"
            }[5m]))
            """;

    public static final String DB_OPERATION_P95_BY_SYSTEM_AND_OPERATION = """
            histogram_quantile(
              0.95,
              sum by (db_system, operation, le) (
                rate(checkout_db_client_operation_duration_seconds_bucket{
                  service="checkout-api"
                }[5m])
              )
            )
            """;

    public static final String DB_POOL_PENDING_REQUESTS = """
            max by (pool) (
              db_client_connection_pending_requests{service="checkout-api"}
            )
            """;
}