package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

import java.util.List;
import java.util.Locale;

/**
 * Provides a defensive check against dangerous metric labels.
 *
 * This is not a complete security mechanism. It is a lightweight guardrail
 * that helps prevent accidental high-cardinality labels from entering metrics.
 */
public final class MetricCardinalityGuard {

    private static final List<String> FORBIDDEN_LABEL_NAMES = List.of(
            "user_id",
            "userid",
            "user",
            "email",
            "request_id",
            "requestid",
            "trace_id",
            "traceid",
            "span_id",
            "spanid",
            "url",
            "path",
            "raw_path",
            "sql",
            "query",
            "exception_message"
    );

    private MetricCardinalityGuard() {
    }

    public static void validateLabelName(String labelName) {
        if (labelName == null || labelName.isBlank()) {
            throw new IllegalArgumentException("label name must not be blank");
        }

        String normalized = labelName.toLowerCase(Locale.ROOT);

        if (FORBIDDEN_LABEL_NAMES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "forbidden high-cardinality or sensitive metric label: " + labelName
            );
        }
    }

    public static void validateLabelValue(String labelName, String labelValue) {
        validateLabelName(labelName);

        if (labelValue == null || labelValue.isBlank()) {
            throw new IllegalArgumentException("label value must not be blank for label: " + labelName);
        }

        if (labelValue.length() > 120) {
            throw new IllegalArgumentException(
                    "label value is suspiciously long for label: " + labelName
            );
        }
    }
}