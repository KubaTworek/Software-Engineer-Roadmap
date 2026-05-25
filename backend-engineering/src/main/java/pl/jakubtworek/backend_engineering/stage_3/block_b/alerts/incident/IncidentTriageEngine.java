package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.incident;

import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook.IncidentType;

/**
 * Encodes a simple symptom-first incident triage flow.
 *
 * This class intentionally does not pretend to find root cause.
 * It helps choose the first operational path under pressure.
 */
public final class IncidentTriageEngine {

    public IncidentTriageDecision decide(
            boolean userFacingImpact,
            boolean significantTraffic,
            IncidentSignal signal,
            IncidentHop slowestOrFailingHop
    ) {
        if (!significantTraffic) {
            return new IncidentTriageDecision(
                    userFacingImpact,
                    false,
                    slowestOrFailingHop,
                    null,
                    "Create a ticket or keep dashboard-only; traffic is too low for paging."
            );
        }

        if (!userFacingImpact) {
            return new IncidentTriageDecision(
                    false,
                    true,
                    slowestOrFailingHop,
                    null,
                    "Reduce alert noise or downgrade to warning; no user-facing impact was confirmed."
            );
        }

        if (slowestOrFailingHop == IncidentHop.REDIS) {
            return new IncidentTriageDecision(
                    true,
                    true,
                    slowestOrFailingHop,
                    IncidentType.REDIS_DOWN,
                    "Open Redis runbook and check for secondary database load."
            );
        }

        if (slowestOrFailingHop == IncidentHop.DATABASE) {
            return new IncidentTriageDecision(
                    true,
                    true,
                    slowestOrFailingHop,
                    IncidentType.DB_DOWN,
                    "Open database runbook and check pool exhaustion, connectivity, and failover."
            );
        }

        if (signal == IncidentSignal.HIGH_LATENCY || signal == IncidentSignal.HIGH_ERROR_RATE) {
            return new IncidentTriageDecision(
                    true,
                    true,
                    slowestOrFailingHop,
                    IncidentType.LATENCY_SPIKE,
                    "Open latency runbook, inspect golden signals, and use exemplars or traces."
            );
        }

        return new IncidentTriageDecision(
                true,
                true,
                slowestOrFailingHop,
                IncidentType.LATENCY_SPIKE,
                "Open golden signals dashboard and continue mitigation-first diagnosis."
        );
    }
}