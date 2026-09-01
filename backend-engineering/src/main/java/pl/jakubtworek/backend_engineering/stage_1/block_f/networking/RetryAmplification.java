package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import java.util.List;

/** Calculates worst-case amplification when multiple network layers retry independently. */
public final class RetryAmplification {

    private RetryAmplification() {}

    public static long downstreamAttempts(List<Integer> attemptsPerLayer) {
        if (attemptsPerLayer == null || attemptsPerLayer.isEmpty()) {
            throw new IllegalArgumentException("at least one layer is required");
        }
        long attempts = 1;
        for (Integer layerAttempts : attemptsPerLayer) {
            if (layerAttempts == null || layerAttempts < 1) {
                throw new IllegalArgumentException("each layer must execute at least one attempt");
            }
            attempts = Math.multiplyExact(attempts, layerAttempts.longValue());
        }
        return attempts;
    }

    public static boolean fitsBudget(List<Integer> attemptsPerLayer, long retryBudget) {
        if (retryBudget < 1) throw new IllegalArgumentException("retryBudget must be positive");
        return downstreamAttempts(attemptsPerLayer) <= retryBudget;
    }
}
