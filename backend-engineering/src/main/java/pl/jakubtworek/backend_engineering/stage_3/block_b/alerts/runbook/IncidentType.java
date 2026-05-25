package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook;

/**
 * Represents incident categories supported by checkout-api runbooks.
 *
 * Incident categories should map to common responder paths, not to every possible root cause.
 */
public enum IncidentType {

    LATENCY_SPIKE("latency-spike"),
    DB_DOWN("db-down"),
    REDIS_DOWN("redis-down");

    private final String slug;

    IncidentType(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }
}