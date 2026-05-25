package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

/**
 * Attribute keys used by manual spans.
 *
 * Some keys mirror OpenTelemetry semantic conventions.
 * Application-specific attributes should use a clear namespace, for example "app.order.id".
 */
public final class TracingAttributes {

    private TracingAttributes() {
    }

    public static final String SERVICE_NAME = "service.name";
    public static final String DEPLOYMENT_ENVIRONMENT_NAME = "deployment.environment.name";

    public static final String HTTP_REQUEST_METHOD = "http.request.method";
    public static final String HTTP_ROUTE = "http.route";
    public static final String HTTP_RESPONSE_STATUS_CODE = "http.response.status_code";

    public static final String DB_SYSTEM_NAME = "db.system.name";
    public static final String DB_OPERATION_NAME = "db.operation.name";
    public static final String DB_QUERY_SUMMARY = "db.query.summary";
    public static final String DB_NAMESPACE = "db.namespace";

    public static final String SERVER_ADDRESS = "server.address";
    public static final String SERVER_PORT = "server.port";

    public static final String APP_ORDER_ID = "app.order.id";
    public static final String CACHE_HIT = "cache.hit";
    public static final String ERROR_TYPE = "error.type";
}