package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.incident;

import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook.IncidentType;

/**
 * Represents the next operational action after initial triage.
 *
 * The goal is to guide responders toward the correct runbook or reduce alert noise.
 */
public record IncidentTriageDecision(
        boolean userFacing,
        boolean significantTraffic,
        IncidentHop suspectedHop,
        IncidentType recommendedRunbook,
        String action
) {
}