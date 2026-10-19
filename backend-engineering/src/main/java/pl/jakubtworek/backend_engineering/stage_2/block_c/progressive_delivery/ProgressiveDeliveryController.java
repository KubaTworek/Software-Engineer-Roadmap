package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import java.util.Objects;

/** Stateful rollout model; a failed canary automatically restores the stable revision. */
public final class ProgressiveDeliveryController {

    public enum Phase {
        STABLE,
        CANARY,
        PROMOTED,
        ROLLED_BACK
    }

    public record RolloutState(String stableRevision, String candidateRevision, int candidateTrafficPercent, Phase phase) {}

    private String stableRevision;
    private String candidateRevision;
    private int candidateTrafficPercent;
    private Phase phase = Phase.STABLE;

    public ProgressiveDeliveryController(String stableRevision) {
        this.stableRevision = requireRevision(stableRevision);
    }

    public synchronized void startCanary(String revision, int trafficPercent) {
        if (phase == Phase.CANARY) throw new IllegalStateException("a canary is already active");
        if (trafficPercent < 1 || trafficPercent >= 100) throw new IllegalArgumentException("canary traffic must be 1..99");
        candidateRevision = requireRevision(revision);
        if (Objects.equals(stableRevision, candidateRevision)) throw new IllegalArgumentException("candidate must differ from stable");
        candidateTrafficPercent = trafficPercent;
        phase = Phase.CANARY;
    }

    public synchronized CanaryAnalyzer.Analysis apply(CanaryAnalyzer analyzer,
                                                       ServiceMetrics baseline,
                                                       ServiceMetrics canary) {
        if (phase != Phase.CANARY) throw new IllegalStateException("no active canary");
        CanaryAnalyzer.Analysis analysis = analyzer.analyze(baseline, canary);
        if (analysis.decision() == CanaryAnalyzer.Decision.PROMOTE) {
            stableRevision = candidateRevision;
            candidateTrafficPercent = 100;
            phase = Phase.PROMOTED;
        } else if (analysis.decision() == CanaryAnalyzer.Decision.ROLLBACK) {
            candidateTrafficPercent = 0;
            phase = Phase.ROLLED_BACK;
        }
        return analysis;
    }

    public synchronized RolloutState state() {
        return new RolloutState(stableRevision, candidateRevision, candidateTrafficPercent, phase);
    }

    private static String requireRevision(String revision) {
        if (revision == null || revision.isBlank()) throw new IllegalArgumentException("revision is required");
        return revision;
    }
}
