package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

/**
 * Centralized constants for tracing attributes and span names.
 *
 * Keeping names in one place reduces semantic drift between teams and services.
 */
public final class TracingConstants {

    private TracingConstants() {
    }

    public static final String SERVICE_NAME = "checkout-api";
    public static final String DEPLOYMENT_ENVIRONMENT_NAME = "production";

    public static final String PAYMENT_ROUTE = "/orders/:id/pay";
    public static final String PAYMENT_SERVER_SPAN_NAME = "POST /orders/:id/pay";

    public static final String DOMAIN_CHARGE_ORDER_SPAN_NAME = "charge order";

    public static final String REDIS_GET_SPAN_NAME = "GET";
    public static final String POSTGRES_SELECT_ORDERS_SPAN_NAME = "SELECT orders";
    public static final String PAYMENT_PROVIDER_POST_SPAN_NAME = "POST";

    public static final String REDIS_SYSTEM_NAME = "redis";
    public static final String POSTGRESQL_SYSTEM_NAME = "postgresql";

    public static final String REDIS_SERVER_ADDRESS = "redis.prod.svc";
    public static final String POSTGRES_SERVER_ADDRESS = "postgres-rw.prod.svc";
    public static final String PAYMENT_PROVIDER_ADDRESS = "payments.example.net";
}