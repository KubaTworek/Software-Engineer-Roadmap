package pl.jakubtworek.lld.promotion.domain;

import java.util.List;
import java.util.Objects;

/**
 * Drzewo dowodu kwalifikacji. SKIPPED jest jawną niewiedzą, nie wynikiem false.
 */
public record ConditionEvaluation(String description, Outcome outcome, List<ConditionEvaluation> children) {

    public enum Outcome {

        SATISFIED, UNSATISFIED, SKIPPED
    }

    public ConditionEvaluation {
        Objects.requireNonNull(description);
        Objects.requireNonNull(outcome);
        children = List.copyOf(children);
    }

    public static ConditionEvaluation leaf(String description, boolean matched) {
        return new ConditionEvaluation(description, matched ? Outcome.SATISFIED : Outcome.UNSATISFIED, List.of());
    }

    public boolean matched() {
        return outcome == Outcome.SATISFIED;
    }
}
