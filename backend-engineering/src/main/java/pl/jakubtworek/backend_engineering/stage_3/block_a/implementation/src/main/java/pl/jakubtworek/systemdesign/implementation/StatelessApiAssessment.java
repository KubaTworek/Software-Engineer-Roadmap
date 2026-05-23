package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

import java.util.List;

/**
 * Assesses whether an API is safe to scale horizontally.
 *
 * Stateless does not mean "no state exists".
 * It means that replicas are interchangeable because critical state is stored outside the process.
 */
public record StatelessApiAssessment(
        String serviceName,
        List<StatefulRisk> statefulRisks
) {
    public StatelessApiAssessment {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName is required");
        }
        if (statefulRisks == null) {
            throw new IllegalArgumentException("statefulRisks is required");
        }
    }

    /**
     * Returns true when no critical local state prevents safe horizontal scaling.
     */
    public boolean isHorizontallyScalable() {
        return statefulRisks.stream().noneMatch(StatefulRisk::blocksSafeHorizontalScaling);
    }
}
