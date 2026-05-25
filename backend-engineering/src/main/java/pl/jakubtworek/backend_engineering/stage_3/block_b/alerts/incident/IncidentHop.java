package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.incident;

/**
 * Represents the most suspicious hop after inspecting metrics, exemplars, or traces.
 *
 * This is not final root cause. It is a triage direction.
 */
public enum IncidentHop {

    REDIS,
    DATABASE,
    EXTERNAL_API,
    APPLICATION,
    NO_CLEAR_HOP
}