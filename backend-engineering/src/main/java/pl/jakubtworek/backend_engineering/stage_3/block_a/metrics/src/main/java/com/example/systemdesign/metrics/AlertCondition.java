package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

/**
 * Functional condition used by alert rules.
 */
@FunctionalInterface
public interface AlertCondition {

    boolean matches(MetricSnapshot snapshot);
}