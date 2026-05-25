package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

/**
 * Centralized label names used by Prometheus metrics.
 *
 * Labels must be bounded-cardinality. Never add user_id, email, request_id,
 * trace_id, raw URL, full SQL query, or exception message as a metric label.
 */
public final class MetricLabels {

    private MetricLabels() {
    }

    public static final String SERVICE = "service";
    public static final String ROUTE = "route";
    public static final String METHOD = "method";
    public static final String STATUS_CODE = "status_code";
    public static final String STATUS_CLASS = "status_class";

    public static final String OPERATION = "operation";
    public static final String RESULT = "result";

    public static final String DB_SYSTEM = "db_system";
    public static final String PROVIDER = "provider";
    public static final String POOL = "pool";
}