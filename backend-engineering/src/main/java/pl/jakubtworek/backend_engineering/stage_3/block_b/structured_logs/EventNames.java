package pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs;

/**
 * Defines stable event names used in structured logs.
 *
 * Avoid free-form event names. They should behave like an API contract
 * between the application and downstream observability tools.
 */
public final class EventNames {

    private EventNames() {
    }

    public static final String HTTP_REQUEST_COMPLETED = "http.request.completed";
    public static final String HTTP_REQUEST_FAILED = "http.request.failed";

    public static final String CACHE_LOOKUP = "cache.lookup";
    public static final String CACHE_LOOKUP_FAILED = "cache.lookup.failed";

    public static final String DB_QUERY_COMPLETED = "db.query.completed";
    public static final String DB_QUERY_FAILED = "db.query.failed";

    public static final String EXTERNAL_API_REQUEST_COMPLETED = "external_api.request.completed";
    public static final String EXTERNAL_API_REQUEST_FAILED = "external_api.request.failed";
}