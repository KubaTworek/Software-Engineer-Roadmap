package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests;

import java.util.List;

/** Reproducible experiment contract committed before the test starts. */
public record PerformanceExperimentPlan(
        String name,
        CapacityHypothesis capacityHypothesis,
        PerformanceObjectives objectives,
        ExperimentGuardrails guardrails,
        List<WorkloadPhase> phases) {

    public PerformanceExperimentPlan {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (capacityHypothesis == null || objectives == null || guardrails == null) {
            throw new IllegalArgumentException("capacity hypothesis, objectives and guardrails are required");
        }
        if (phases == null || phases.isEmpty()) throw new IllegalArgumentException("at least one phase is required");
        phases = List.copyOf(phases);
    }
}
