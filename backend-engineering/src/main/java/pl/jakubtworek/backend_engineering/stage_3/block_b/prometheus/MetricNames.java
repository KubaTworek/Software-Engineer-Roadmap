package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

/**
 * Centralized metric names used by checkout-api.
 *
 * Metric names should include the domain, measured concept, unit when applicable,
 * and Prometheus-compatible suffixes such as "_total" for counters.
 */
public final class MetricNames {

    private MetricNames() {
    }

    public static final String HTTP_REQUESTS_TOTAL =
            "checkout_http_requests_total";

    public static final String HTTP_REQUEST_DURATION_SECONDS =
            "checkout_http_request_duration_seconds";

    public static final String HTTP_INFLIGHT_REQUESTS =
            "checkout_http_inflight_requests";

    public static final String CACHE_REQUESTS_TOTAL =
            "checkout_cache_requests_total";

    public static final String DB_CLIENT_OPERATION_DURATION_SECONDS =
            "checkout_db_client_operation_duration_seconds";

    public static final String PAYMENT_PROVIDER_REQUESTS_TOTAL =
            "checkout_payment_provider_requests_total";

    public static final String PAYMENT_PROVIDER_DURATION_SECONDS =
            "checkout_payment_provider_duration_seconds";

    public static final String DB_CLIENT_CONNECTION_PENDING_REQUESTS =
            "db_client_connection_pending_requests";

    public static final String DB_CLIENT_CONNECTION_TIMEOUTS_TOTAL =
            "db_client_connection_timeouts_total";
}