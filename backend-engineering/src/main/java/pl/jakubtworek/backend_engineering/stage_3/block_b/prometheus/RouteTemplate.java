package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

import java.util.Objects;

/**
 * Represents a low-cardinality HTTP route template.
 *
 * Use values such as "/orders/:id/pay", not raw paths like "/orders/123/pay".
 * Raw URLs can create unbounded cardinality in Prometheus.
 */
public final class RouteTemplate {

    private final String value;

    private RouteTemplate(String value) {
        this.value = validate(value);
    }

    public static RouteTemplate of(String value) {
        return new RouteTemplate(value);
    }

    public String value() {
        return value;
    }

    private static String validate(String value) {
        Objects.requireNonNull(value, "route template must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("route template must not be blank");
        }

        if (!value.startsWith("/")) {
            throw new IllegalArgumentException("route template must start with '/'");
        }

        return value;
    }
}