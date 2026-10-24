package pl.jakubtworek.lld.promotion.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Specyfikacje i kompozycje zwracają wyjaśnienie w tym samym przebiegu, w którym
 * rozstrzygają warunek. Nie uruchamiamy ich ponownie na potrzeby raportu.
 * W pominiętej gałęzi zachowujemy strukturę, lecz nie pobieramy jej faktów.
 */
public final class Conditions {

    private Conditions() {
    }

    public static Condition always() {
        return new Leaf("ALWAYS", c -> "", c -> true);
    }

    public static Condition minimumSubtotal(Money minimum) {
        Objects.requireNonNull(minimum);
        return new Leaf("MINIMUM_SUBTOTAL", c -> "expected=" + minimum + ", actual=" + c.basket().subtotal(),
                c -> c.basket().subtotal().compareTo(minimum) >= 0);
    }

    public static Condition tier(CustomerTier expected) {
        Objects.requireNonNull(expected);
        return new Leaf("TIER", c -> "expected=" + expected + ", actual=" + c.tier(), c -> c.tier() == expected);
    }

    public static Condition coupon(String code) {
        String normalized = PricingContext.normalizeCoupon(code);
        return new Leaf("COUPON", c -> "expected=" + normalized + ", actual=" + c.coupons().contains(normalized),
                c -> c.coupons().contains(normalized));
    }

    public static Condition category(String category) {
        if (category == null || !category.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Niepoprawna kategoria");
        return new Leaf("CATEGORY_PRESENT", c -> "expected=" + category + ", actual=" + c.basket()
                .containsCategory(category), c -> c.basket().containsCategory(category));
    }

    public static Condition allOf(Condition... conditions) {
        return composite(true, conditions);
    }

    public static Condition anyOf(Condition... conditions) {
        return composite(false, conditions);
    }

    private static Condition composite(boolean all, Condition[] conditions) {
        List<Condition> copy = List.of(conditions);
        if (copy.isEmpty())
            throw new IllegalArgumentException("Kompozycja wymaga co najmniej jednego warunku");
        return new Composite(all, copy);
    }

    private record Leaf(String name, Function<PricingContext, String> facts,
            Predicate<PricingContext> predicate) implements Condition {

        @Override
        public boolean matches(PricingContext context) {
            return evaluate(context).matched();
        }

        @Override
        public ConditionEvaluation evaluate(PricingContext context) {
            Objects.requireNonNull(context);
            boolean matched = predicate.test(context);
            return ConditionEvaluation.leaf(name + " " + facts.apply(context), matched);
        }

        @Override
        public ConditionEvaluation skipped() {
            return new ConditionEvaluation(name, ConditionEvaluation.Outcome.SKIPPED, List.of());
        }
    }

    private record Composite(boolean all, List<Condition> children) implements Condition {

        @Override
        public boolean matches(PricingContext context) {
            return evaluate(context).matched();
        }

        @Override
        public ConditionEvaluation evaluate(PricingContext context) {
            Objects.requireNonNull(context);
            var trace = new ArrayList<ConditionEvaluation>();
            boolean decided = false, result = all;
            for (Condition child : children) {
                if (decided)
                    trace.add(child.skipped());
                else {
                    ConditionEvaluation evaluation = Objects.requireNonNull(child.evaluate(context));
                    if (evaluation.outcome() == ConditionEvaluation.Outcome.SKIPPED) {
                        throw new IllegalStateException("Oceniany warunek nie może zwrócić SKIPPED");
                    }
                    trace.add(evaluation);
                    if (all != evaluation.matched()) {
                        result = !all;
                        decided = true;
                    }
                }
            }
            return new ConditionEvaluation(all ? "AND" : "OR",
                    result ? ConditionEvaluation.Outcome.SATISFIED : ConditionEvaluation.Outcome.UNSATISFIED, trace);
        }

        @Override
        public ConditionEvaluation skipped() {
            return new ConditionEvaluation(all ? "AND" : "OR", ConditionEvaluation.Outcome.SKIPPED, children.stream()
                    .map(Condition::skipped).toList());
        }
    }
}
