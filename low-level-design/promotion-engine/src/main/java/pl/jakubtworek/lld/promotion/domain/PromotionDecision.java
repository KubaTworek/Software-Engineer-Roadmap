package pl.jakubtworek.lld.promotion.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Decyzja oddziela brak kalkulacji od kalkulacji zero. Przy NOT_SELECTED kalkulacja
 * może opisywać niewybraną alternatywę BEST_SINGLE; applied nadal wynosi zero.
 * Wyjaśnienie kwalifikacji powstaje podczas oceny, nie przez ponowienie predykatów.
 */
public record PromotionDecision(PromotionId id, DecisionStatus status, Optional<DiscountCalculation> calculation,
        Money remainingAfter, Optional<PromotionId> blocker, ConditionEvaluation qualification) {

    public PromotionDecision {
        Objects.requireNonNull(id);
        Objects.requireNonNull(status);
        Objects.requireNonNull(calculation);
        Objects.requireNonNull(remainingAfter);
        Objects.requireNonNull(blocker);
        Objects.requireNonNull(qualification);
        boolean positive = calculation.map(c -> c.allowed().cents() > 0).orElse(false);
        if (status == DecisionStatus.APPLIED && !positive)
            throw new IllegalArgumentException("Zastosowanie wymaga dodatniej kalkulacji");
        if (status == DecisionStatus.NO_REDUCTION && (calculation.isEmpty() || calculation.get().requested()
                .cents() != 0)) {
            throw new IllegalArgumentException("Brak redukcji wymaga wyliczonego zera");
        }
        if (status != DecisionStatus.APPLIED && status != DecisionStatus.NO_REDUCTION
                && status != DecisionStatus.NOT_SELECTED && calculation.isPresent()) {
            throw new IllegalArgumentException("Pominięcie nie może udawać kalkulacji");
        }
        boolean conflict = status == DecisionStatus.BLOCKED_EXCLUSIVE || status == DecisionStatus.EXCLUDED;
        if (conflict && blocker.isEmpty() || !conflict && status != DecisionStatus.NOT_SELECTED
                && blocker.isPresent()) {
            throw new IllegalArgumentException("Niespójna przyczyna blokady");
        }
    }

    public Money applied() {
        return status == DecisionStatus.APPLIED ? calculation.orElseThrow().allowed() : Money.ZERO;
    }

    public Optional<Money> requested() {
        return calculation.map(DiscountCalculation::requested);
    }
}
