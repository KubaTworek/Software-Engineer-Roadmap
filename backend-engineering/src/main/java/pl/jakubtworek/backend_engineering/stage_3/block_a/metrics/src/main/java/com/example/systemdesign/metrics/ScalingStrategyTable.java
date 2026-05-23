package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

import java.util.List;

/**
 * Provides a conceptual comparison of vertical, horizontal, and hybrid scaling.
 */
public final class ScalingStrategyTable {

    private ScalingStrategyTable() {
        // Utility class.
    }

    public static List<ScalingStrategyComparison> defaultComparisons() {
        return List.of(
                new ScalingStrategyComparison(
                        ScalingStrategy.VERTICAL,
                        "Monoliths, single databases, or fast capacity increase without refactoring.",
                        "Smallest operational and code change; fast relief.",
                        "Hard limit of one machine, weaker high availability, expensive upper end of scale.",
                        "Buys time, but does not solve resilience well."
                ),
                new ScalingStrategyComparison(
                        ScalingStrategy.HORIZONTAL,
                        "Stateless APIs, web tiers, worker pools, and high availability requirements.",
                        "Better resilience, rolling updates, elasticity, and autoscaling.",
                        "Requires load balancing, externalized state, and strict limits on dependencies.",
                        "Default choice for APIs and workers."
                ),
                new ScalingStrategyComparison(
                        ScalingStrategy.HYBRID,
                        "Stateful components scale up while API and worker tiers scale out.",
                        "Often the best compromise between cost, risk, and operational simplicity.",
                        "Requires modeling two different types of limits.",
                        "The most common practical architecture."
                )
        );
    }
}
