package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

/**
 * Describes the capacity of one critical component.
 *
 * A useful system design review should be able to fill this structure
 * for every important dependency: API, database, cache, queue, external API,
 * connection pool, or worker group.
 */
public record CapacityReport(
        String componentName,
        String capacityFormula,
        String measuredParameter,
        double currentLimit,
        String confirmingMetric,
        String expectedBottleneckReason
) {
    public CapacityReport {
        if (componentName == null || componentName.isBlank()) {
            throw new IllegalArgumentException("componentName is required");
        }
        if (capacityFormula == null || capacityFormula.isBlank()) {
            throw new IllegalArgumentException("capacityFormula is required");
        }
        if (measuredParameter == null || measuredParameter.isBlank()) {
            throw new IllegalArgumentException("measuredParameter is required");
        }
        if (currentLimit < 0) {
            throw new IllegalArgumentException("currentLimit must be non-negative");
        }
        if (confirmingMetric == null || confirmingMetric.isBlank()) {
            throw new IllegalArgumentException("confirmingMetric is required");
        }
        if (expectedBottleneckReason == null || expectedBottleneckReason.isBlank()) {
            throw new IllegalArgumentException("expectedBottleneckReason is required");
        }
    }
}
