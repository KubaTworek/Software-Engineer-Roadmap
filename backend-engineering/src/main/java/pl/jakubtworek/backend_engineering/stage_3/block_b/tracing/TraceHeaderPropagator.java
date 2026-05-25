package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Injects W3C trace context into outbound request headers.
 *
 * This class is mainly useful for custom HTTP clients.
 * Standard instrumented clients usually inject traceparent automatically.
 */
public final class TraceHeaderPropagator {

    private static final TextMapSetter<Map<String, String>> MAP_SETTER =
            (carrier, key, value) -> {
                if (carrier != null && key != null && value != null) {
                    carrier.put(key, value);
                }
            };

    private final OpenTelemetry openTelemetry;

    public TraceHeaderPropagator(OpenTelemetry openTelemetry) {
        this.openTelemetry = Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
    }

    public Map<String, String> injectCurrentContext(Map<String, String> existingHeaders) {
        Map<String, String> headers = existingHeaders == null
                ? new HashMap<>()
                : new HashMap<>(existingHeaders);

        openTelemetry.getPropagators()
                .getTextMapPropagator()
                .inject(Context.current(), headers, MAP_SETTER);

        return headers;
    }

    public Map<String, String> injectCurrentContextWithRequestId(
            Map<String, String> existingHeaders,
            RequestCorrelation requestCorrelation
    ) {
        Map<String, String> headers = injectCurrentContext(existingHeaders);
        headers.put("x-request-id", requestCorrelation.requestId());
        return headers;
    }
}