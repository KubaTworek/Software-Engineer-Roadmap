package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests;

import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.capacity.Bottleneck;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.capacity.BottleneckAnalyzer;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.capacity.CapacityPlan;

/** Capacity estimate recorded before traffic is generated. */
public record CapacityHypothesis(Bottleneck firstBottleneck, double safeRps, double headroomFraction) {

    public CapacityHypothesis {
        if (firstBottleneck == null || !firstBottleneck.appliesToPath()) {
            throw new IllegalArgumentException("a finite first bottleneck is required");
        }
        if (!Double.isFinite(headroomFraction) || headroomFraction <= 0 || headroomFraction >= 1) {
            throw new IllegalArgumentException("headroomFraction must be in range (0, 1)");
        }
        if (!Double.isFinite(safeRps) || safeRps <= 0 || safeRps >= firstBottleneck.limitRps()) {
            throw new IllegalArgumentException("safeRps must be positive and below modeled limit");
        }
    }

    public static CapacityHypothesis from(CapacityPlan plan, double headroomFraction) {
        Bottleneck bottleneck = new BottleneckAnalyzer().first(plan);
        return new CapacityHypothesis(
                bottleneck,
                bottleneck.limitRps() * (1.0 - headroomFraction),
                headroomFraction);
    }
}
