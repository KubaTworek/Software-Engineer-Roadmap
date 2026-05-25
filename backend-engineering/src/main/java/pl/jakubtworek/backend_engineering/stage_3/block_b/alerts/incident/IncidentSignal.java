package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.incident;

/**
 * Represents the first observed signal during incident triage.
 *
 * The model starts with symptoms rather than presumed causes.
 */
public enum IncidentSignal {

    HIGH_LATENCY,
    HIGH_ERROR_RATE,
    LOW_TRAFFIC,
    REDIS_ERRORS,
    DATABASE_TIMEOUTS,
    EXTERNAL_API_ERRORS,
    SATURATION
}