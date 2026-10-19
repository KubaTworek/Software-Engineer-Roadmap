package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime guardrail limiting distinct values observed for a metric label.
 *
 * Name validation catches obvious mistakes such as trace_id. This budget also
 * catches an apparently innocent label, such as route or tenant, whose number
 * of distinct values keeps growing at runtime. It is local to one process and
 * complements, rather than replaces, backend-side cardinality monitoring.
 */
public final class MetricCardinalityBudget {

    private final int maximumDistinctValuesPerLabel;
    private final Map<String, Set<String>> observedValues = new ConcurrentHashMap<>();

    public MetricCardinalityBudget(int maximumDistinctValuesPerLabel) {
        if (maximumDistinctValuesPerLabel < 1) {
            throw new IllegalArgumentException("maximumDistinctValuesPerLabel must be positive");
        }
        this.maximumDistinctValuesPerLabel = maximumDistinctValuesPerLabel;
    }

    public void record(String labelName, String labelValue) {
        MetricCardinalityGuard.validateLabelValue(labelName, labelValue);
        Set<String> values = observedValues.computeIfAbsent(
                labelName,
                ignored -> java.util.Collections.synchronizedSet(new HashSet<>())
        );

        synchronized (values) {
            if (!values.contains(labelValue)
                    && values.size() >= maximumDistinctValuesPerLabel) {
                throw new IllegalStateException(
                        "cardinality budget exceeded for label: " + labelName);
            }
            values.add(labelValue);
        }
    }

    public int distinctValues(String labelName) {
        Set<String> values = observedValues.get(labelName);
        if (values == null) {
            return 0;
        }
        synchronized (values) {
            return values.size();
        }
    }
}
