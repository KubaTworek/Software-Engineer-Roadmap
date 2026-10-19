package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import java.util.UUID;

/**
 * Represents request-level correlation outside of OpenTelemetry trace context.
 *
 * requestId is useful for support workflows and ingress correlation.
 * It is not a replacement for trace_id and should not be used as a Prometheus label.
 */
public record RequestCorrelation(String requestId) {

    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final String SAFE_REQUEST_ID = "[A-Za-z0-9._:-]+";

    public RequestCorrelation {
        if (!isSafe(requestId)) {
            throw new IllegalArgumentException("requestId must use safe characters and be at most 128 characters");
        }
    }

    public static RequestCorrelation fromHeaderOrGenerate(String headerValue) {
        if (isSafe(headerValue)) {
            return new RequestCorrelation(headerValue);
        }

        return new RequestCorrelation("req-" + UUID.randomUUID().toString().replace("-", ""));
    }

    private static boolean isSafe(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= MAX_REQUEST_ID_LENGTH
                && value.matches(SAFE_REQUEST_ID);
    }
}
