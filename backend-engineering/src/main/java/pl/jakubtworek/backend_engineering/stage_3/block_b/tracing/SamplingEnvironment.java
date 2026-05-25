package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import java.util.Map;

/**
 * Documents recommended tracing sampler environment variables.
 *
 * The application does not need to hardcode sampling policy if it is configured
 * through environment variables or the OpenTelemetry Collector.
 */
public final class SamplingEnvironment {

    private SamplingEnvironment() {
    }

    public static Map<String, String> parentBasedTraceIdRatioSampler(double ratio) {
        if (ratio < 0.0 || ratio > 1.0) {
            throw new IllegalArgumentException("sampling ratio must be between 0.0 and 1.0");
        }

        return Map.of(
                "OTEL_TRACES_SAMPLER", "parentbased_traceidratio",
                "OTEL_TRACES_SAMPLER_ARG", String.valueOf(ratio)
        );
    }
}