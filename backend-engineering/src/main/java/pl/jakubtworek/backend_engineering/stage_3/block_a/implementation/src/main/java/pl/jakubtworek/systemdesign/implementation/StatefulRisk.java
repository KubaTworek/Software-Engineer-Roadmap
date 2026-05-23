package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Represents a risk created by keeping state in the wrong place.
 */
public record StatefulRisk(
        String component,
        StateLocation stateLocation,
        boolean criticalState,
        String risk,
        String recommendedMitigation
) {
    public StatefulRisk {
        requireText(component, "component");
        if (stateLocation == null) {
            throw new IllegalArgumentException("stateLocation is required");
        }
        requireText(risk, "risk");
        requireText(recommendedMitigation, "recommendedMitigation");
    }

    /**
     * Critical state in process memory or local ephemeral disk usually prevents safe horizontal scaling.
     */
    public boolean blocksSafeHorizontalScaling() {
        return criticalState
                && (stateLocation == StateLocation.PROCESS_MEMORY
                || stateLocation == StateLocation.LOCAL_EPHEMERAL_DISK);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
