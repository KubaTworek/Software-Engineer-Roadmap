package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import java.util.UUID;

/**
 * Represents request-level correlation outside of OpenTelemetry trace context.
 *
 * requestId is useful for support workflows and ingress correlation.
 * It is not a replacement for trace_id and should not be used as a Prometheus label.
 */
public record RequestCorrelation(String requestId) {

    public RequestCorrelation {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }

    public static RequestCorrelation fromHeaderOrGenerate(String headerValue) {
        if (headerValue != null && !headerValue.isBlank()) {
            return new RequestCorrelation(headerValue);
        }

        return new RequestCorrelation("req-" + UUID.randomUUID().toString().replace("-", ""));
    }
}