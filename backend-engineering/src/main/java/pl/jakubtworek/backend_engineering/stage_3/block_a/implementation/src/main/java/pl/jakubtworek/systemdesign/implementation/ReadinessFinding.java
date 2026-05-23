package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Represents a finding from a production readiness review.
 */
public record ReadinessFinding(
        String area,
        boolean passed,
        String explanation,
        String recommendedAction
) {
    public ReadinessFinding {
        requireText(area, "area");
        requireText(explanation, "explanation");
        requireText(recommendedAction, "recommendedAction");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
