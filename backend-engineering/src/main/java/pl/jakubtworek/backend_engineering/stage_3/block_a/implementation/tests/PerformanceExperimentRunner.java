package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests;

import java.util.ArrayList;
import java.util.List;

/** Runs observation windows and stops before the next phase when a guardrail is crossed. */
public final class PerformanceExperimentRunner {

    private final ObservationProbe probe;

    public PerformanceExperimentRunner(ObservationProbe probe) {
        if (probe == null) throw new IllegalArgumentException("probe is required");
        this.probe = probe;
    }

    public ExperimentReport run(PerformanceExperimentPlan plan) throws Exception {
        if (plan == null) throw new IllegalArgumentException("plan is required");
        List<ExperimentObservation> observations = new ArrayList<>();
        for (WorkloadPhase phase : plan.phases()) {
            ExperimentObservation observation = probe.execute(phase);
            observations.add(observation);
            List<String> violations = plan.guardrails().violations(observation);
            if (!violations.isEmpty()) {
                return new ExperimentReport(true, phase.name(), violations, observations);
            }
        }
        return new ExperimentReport(false, null, List.of(), observations);
    }

    @FunctionalInterface
    public interface ObservationProbe {
        ExperimentObservation execute(WorkloadPhase phase) throws Exception;
    }

    public record ExperimentReport(
            boolean aborted,
            String abortedAfterPhase,
            List<String> abortReasons,
            List<ExperimentObservation> observations) {

        public ExperimentReport {
            abortReasons = List.copyOf(abortReasons);
            observations = List.copyOf(observations);
        }
    }
}
