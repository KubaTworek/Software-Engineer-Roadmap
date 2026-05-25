package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts;

import java.util.List;
import java.util.Objects;

/**
 * Represents one Alertmanager routing rule.
 *
 * Matchers decide where alerts are sent based on labels.
 */
public final class AlertmanagerRoute {

    private final String receiver;
    private final List<String> matchers;

    public AlertmanagerRoute(String receiver, List<String> matchers) {
        this.receiver = requireNonBlank(receiver, "receiver");
        this.matchers = List.copyOf(Objects.requireNonNull(matchers, "matchers must not be null"));
    }

    public String receiver() {
        return receiver;
    }

    public List<String> matchers() {
        return matchers;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}