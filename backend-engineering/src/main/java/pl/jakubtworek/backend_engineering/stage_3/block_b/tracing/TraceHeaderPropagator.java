package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
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

    private static final TextMapGetter<Map<String, String>> MAP_GETTER =
            new TextMapGetter<>() {
                @Override
                public Iterable<String> keys(Map<String, String> carrier) {
                    return carrier == null ? java.util.List.of() : carrier.keySet();
                }

                @Override
                public String get(Map<String, String> carrier, String key) {
                    if (carrier == null || key == null) {
                        return null;
                    }
                    return carrier.entrySet().stream()
                            .filter(entry -> entry.getKey().equalsIgnoreCase(key))
                            .map(Map.Entry::getValue)
                            .findFirst()
                            .orElse(null);
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

    /**
     * Extracts an inbound W3C trace context without making it current yet.
     * The caller should pass the returned context as the explicit parent of
     * the SERVER or CONSUMER span created at the process boundary.
     */
    public Context extractContext(Map<String, String> headers) {
        return openTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.root(), headers == null ? Map.of() : headers, MAP_GETTER);
    }

    public Map<String, String> injectCurrentContextWithRequestId(
            Map<String, String> existingHeaders,
            RequestCorrelation requestCorrelation
    ) {
        Objects.requireNonNull(requestCorrelation, "requestCorrelation must not be null");
        Map<String, String> headers = injectCurrentContext(existingHeaders);
        headers.put("x-request-id", requestCorrelation.requestId());
        return headers;
    }
}
