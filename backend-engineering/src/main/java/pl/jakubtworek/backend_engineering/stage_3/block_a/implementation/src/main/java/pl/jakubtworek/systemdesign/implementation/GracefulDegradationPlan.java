package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

import java.util.Set;

/**
 * Describes how a system preserves critical functionality
 * while degrading optional features.
 */
public record GracefulDegradationPlan(
        String criticalUserJourney,
        Set<String> optionalFeatures,
        Set<EmergencyLever> emergencyLevers,
        String userVisibleBehavior
) {
    public GracefulDegradationPlan {
        requireText(criticalUserJourney, "criticalUserJourney");
        if (optionalFeatures == null || optionalFeatures.isEmpty()) {
            throw new IllegalArgumentException("optionalFeatures must not be empty");
        }
        if (emergencyLevers == null || emergencyLevers.isEmpty()) {
            throw new IllegalArgumentException("emergencyLevers must not be empty");
        }
        requireText(userVisibleBehavior, "userVisibleBehavior");
    }

    /**
     * A useful degradation plan should have at least one operational lever.
     */
    public boolean canBeActivatedOperationally() {
        return !emergencyLevers.isEmpty();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
