package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics;

/**
 * Functional condition used by alert rules.
 */
@FunctionalInterface
public interface AlertCondition {

    boolean matches(MetricSnapshot snapshot);
}