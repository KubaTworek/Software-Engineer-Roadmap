package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.scaling;

/**
 * Chooses a scaling strategy using practical system-design heuristics.
 */
public class ScalingStrategyAdvisor {

    public ScalingDecision decide(ScalingInput input) {
        if (input.stateless() && (input.workerOrApiTier() || input.requiresHighAvailability())) {
            return new ScalingDecision(
                    ScalingStrategy.HORIZONTAL,
                    "Stateless API or worker tier can scale out behind a load balancer.",
                    "Dependencies such as DB, Redis, and external APIs may become the real bottleneck."
            );
        }

        if (input.singleStatefulComponent() && input.refactorIsExpensive()) {
            return new ScalingDecision(
                    ScalingStrategy.VERTICAL,
                    "Stateful component is expensive to split, so vertical scaling can buy time.",
                    "One-machine limit and weaker high availability remain unresolved."
            );
        }

        return new ScalingDecision(
                ScalingStrategy.HYBRID,
                "Use horizontal scaling for stateless tiers and vertical or specialized scaling for stateful components.",
                "Two different types of limits must be modeled and tested."
        );
    }
}