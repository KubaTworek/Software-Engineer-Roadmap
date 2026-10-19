package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.incident;

import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook.IncidentType;

import java.util.Objects;

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
    public IncidentTriageDecision {
        suspectedHop = Objects.requireNonNull(suspectedHop, "suspectedHop must not be null");
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        if (userFacing && significantTraffic && recommendedRunbook == null) {
            throw new IllegalArgumentException(
                    "a user-facing incident with significant traffic must recommend a runbook"
            );
        }
    }
}
