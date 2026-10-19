package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import java.util.ArrayList;
import java.util.List;

/** Makes a fail-closed canary decision from comparable baseline and candidate windows. */
public final class CanaryAnalyzer {

    public enum Decision {
        PROMOTE,
        HOLD,
        ROLLBACK
    }

    public record Policy(long minimumCanaryRequests, double maximumErrorRate,
                         double maximumErrorRateIncrease, double maximumP99Ratio) {
        public Policy {
            if (minimumCanaryRequests < 1) throw new IllegalArgumentException("minimumCanaryRequests must be positive");
            if (maximumErrorRate < 0 || maximumErrorRate > 1) throw new IllegalArgumentException("invalid error rate");
            if (maximumErrorRateIncrease < 0 || maximumErrorRateIncrease > 1) {
                throw new IllegalArgumentException("invalid error rate increase");
            }
            if (maximumP99Ratio < 1 || !Double.isFinite(maximumP99Ratio)) {
                throw new IllegalArgumentException("maximumP99Ratio must be finite and at least one");
            }
        }
    }

    public record Analysis(Decision decision, List<String> reasons) {
        public Analysis {
            reasons = List.copyOf(reasons);
        }
    }

    private final Policy policy;

    public CanaryAnalyzer(Policy policy) {
        this.policy = policy;
    }

    public Analysis analyze(ServiceMetrics baseline, ServiceMetrics canary) {
        if (baseline.requests() < policy.minimumCanaryRequests()
                || canary.requests() < policy.minimumCanaryRequests()) {
            return new Analysis(Decision.HOLD, List.of("insufficient comparable sample"));
        }

        List<String> violations = new ArrayList<>();
        if (canary.errorRate() > policy.maximumErrorRate()) {
            violations.add("canary error rate exceeds absolute limit");
        }
        if (canary.errorRate() - baseline.errorRate() > policy.maximumErrorRateIncrease()) {
            violations.add("canary error rate regressed against baseline");
        }
        double p99Limit = baseline.p99Millis() * policy.maximumP99Ratio();
        if (canary.p99Millis() > p99Limit) {
            violations.add("canary p99 regressed against baseline");
        }
        return violations.isEmpty()
                ? new Analysis(Decision.PROMOTE, List.of("error rate and p99 are within policy"))
                : new Analysis(Decision.ROLLBACK, violations);
    }
}
