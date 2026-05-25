package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.scaling;

/**
 * Component characteristics used to choose a scaling strategy.
 */
public record ScalingInput(
        boolean stateless,
        boolean requiresHighAvailability,
        boolean refactorIsExpensive,
        boolean singleStatefulComponent,
        boolean workerOrApiTier
) {
}