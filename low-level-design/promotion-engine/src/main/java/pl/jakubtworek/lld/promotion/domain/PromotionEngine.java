package pl.jakubtworek.lld.promotion.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import static pl.jakubtworek.lld.promotion.domain.DecisionStatus.*;

/**
 * Wspólna ścieżka oceny, ograniczania i alokacji dla obu polityk wyboru.
 * Clock odczytujemy raz na quote, kwalifikację każdej promocji najwyżej raz.
 * Lokalny cache kalkulacji samodzielnych ofert nie przechodzi do następnej wyceny.
 * Nie rezerwujemy kuponów i nie publikujemy częściowego raportu po błędzie.
 */
public final class PromotionEngine {

    private final List<Promotion> promotions;
    private final java.util.BitSet[] conflicts;
    private final Map<PromotionId, Integer> positions;
    private final Money maximumDiscount;
    private final Clock clock;
    private final SelectionPolicy selection;

    private record Qualification(boolean active, ConditionEvaluation trace) {
    }

    private record Assessment(DecisionStatus status, Optional<DiscountCalculation> calculation) {

        Money benefit() {
            return calculation.map(DiscountCalculation::allowed).orElse(Money.ZERO);
        }
    }

    public PromotionEngine(List<Promotion> promotions) {
        this(promotions, new Money(Long.MAX_VALUE));
    }

    public PromotionEngine(List<Promotion> promotions, Money maximumDiscount) {
        this(promotions, maximumDiscount, Clock.systemUTC(), new PrioritySelection());
    }

    public PromotionEngine(List<Promotion> promotions, Money maximumDiscount, Clock clock, SelectionPolicy selection) {
        this.maximumDiscount = Objects.requireNonNull(maximumDiscount);
        this.clock = Objects.requireNonNull(clock);
        this.selection = Objects.requireNonNull(selection);
        List<Promotion> copy = List.copyOf(promotions);
        var ids = new HashSet<PromotionId>();
        for (Promotion promotion : copy) {
            if (!ids.add(promotion.id()))
                throw new IllegalArgumentException("Powtórzone ID promocji: " + promotion.id());
        }
        for (Promotion promotion : copy) {
            if (!ids.containsAll(promotion.excludedIds()))
                throw new IllegalArgumentException("Nieznane ID w wykluczeniach: " + promotion.id());
        }
        this.promotions = copy.stream().sorted(Comparator.comparingInt(Promotion::priority).reversed()
                .thenComparing(Promotion::id)).toList();
        var index = new HashMap<PromotionId, Integer>();
        for (int i = 0; i < this.promotions.size(); i++) {
            index.put(this.promotions.get(i).id(), i);
        }
        positions = Map.copyOf(index);
        conflicts = new java.util.BitSet[this.promotions.size()];
        for (int i = 0; i < conflicts.length; i++) {
            conflicts[i] = new java.util.BitSet(conflicts.length);
        }
        // Kompilacja grafu raz dla konfiguracji, zamiast porównywania obiektów
        // każdej pary wybranych ofert przy każdej wycenie.
        for (int i = 0; i < conflicts.length; i++) {
            Promotion left = this.promotions.get(i);
            for (int j = 0; j < conflicts.length; j++) {
                Promotion right = this.promotions.get(j);
                if (left.stacking() == StackingPolicy.EXCLUSIVE || right.stacking() == StackingPolicy.EXCLUSIVE
                        || left.excludedIds().contains(right.id()) || right.excludedIds().contains(left.id())) {
                    conflicts[i].set(j);
                }
            }
        }
    }

    public PricingResult quote(PricingContext context) {
        Objects.requireNonNull(context);
        // Wspólna chwila również wtedy, gdy kampania kończy się w trakcie obliczenia.
        Instant now = clock.instant();
        // Nie przycinamy limitu do subtotal: inaczej raport błędnie nazwałby
        // ograniczenie samym saldem również ograniczeniem globalnego cap.
        Money original = context.basket().subtotal(), initialBudget = maximumDiscount;
        var initialBalances = new TreeMap<String, Money>();
        context.basket().lines().forEach(line -> initialBalances.put(line.sku(), line.total()));
        var qualifications = new HashMap<PromotionId, Qualification>();
        // Kolejność kwalifikowanych ofert jest częścią deterministycznego
        // wyboru i remisów; zwykła HashMap nie zachowałaby kolejności katalogu.
        var eligible = new java.util.LinkedHashMap<PromotionId, Promotion>();
        for (Promotion promotion : promotions) {
            Qualification qualification = qualify(promotion, context, now);
            qualifications.put(promotion.id(), qualification);
            if (qualification.active() && qualification.trace().matched())
                eligible.put(promotion.id(), promotion);
        }
        var standalone = new HashMap<PromotionId, Assessment>();
        Set<PromotionId> chosen = Set.copyOf(selection.select(List.copyOf(eligible.keySet()), id -> {
            Promotion promotion = eligible.get(id);
            if (promotion == null)
                throw new IllegalArgumentException("Polityka ocenia niekwalifikowane ID");
            return standalone.computeIfAbsent(id, key -> assess(promotion, context.basket(), initialBalances,
                    initialBudget)).benefit().cents();
        }));
        if (!eligible.keySet().containsAll(chosen))
            throw new IllegalArgumentException("Polityka wybrała niekwalifikowane ID");
        var balances = new TreeMap<>(initialBalances);
        Money remaining = original, budget = initialBudget;
        var selected = new java.util.BitSet(promotions.size());
        var decisions = new ArrayList<PromotionDecision>();
        for (Promotion promotion : promotions) {
            Qualification qualification = qualifications.get(promotion.id());
            if (!qualification.active() || !qualification.trace().matched()) {
                decisions.add(decision(promotion, qualification.active() ? CONDITION_NOT_MET : NOT_ACTIVE,
                        Optional.empty(), remaining, null, qualification));
                continue;
            }
            if (!chosen.contains(promotion.id())) {
                Assessment alternative = standalone.get(promotion.id());
                DecisionStatus status = alternative == null || alternative.benefit()
                        .cents() > 0 ? NOT_SELECTED : alternative.status();
                decisions.add(decision(promotion, status,
                        alternative == null ? Optional.empty() : alternative.calculation(), remaining, null,
                        qualification));
                continue;
            }
            Promotion blocker = null;
            DecisionStatus conflict = null;
            // Przecięcie wektorów bitów kosztuje O(P/64), a pierwszy bit
            // zachowuje dawny porządek wskazywania blokującej promocji.
            // and mutuje odbiorcę. Bez clone pierwsza wycena skasowałaby
            // część konfliktów we współdzielonej konfiguracji kolejnych wycen.
            var blocked = (java.util.BitSet) conflicts[positions.get(promotion.id())].clone();
            blocked.and(selected);
            int first = blocked.nextSetBit(0);
            if (first >= 0) {
                blocker = promotions.get(first);
                conflict = promotion.stacking() == StackingPolicy.EXCLUSIVE
                        || blocker.stacking() == StackingPolicy.EXCLUSIVE ? BLOCKED_EXCLUSIVE : EXCLUDED;
            }
            if (blocker != null) {
                decisions.add(decision(promotion, conflict, Optional.empty(), remaining, blocker.id(), qualification));
                continue;
            }
            // Cache wolno ponownie użyć tylko dla niezmienionych podstaw i limitu.
            Assessment assessment = balances.equals(initialBalances) && budget.equals(initialBudget)
                    && standalone.containsKey(promotion.id()) ? standalone.get(promotion.id()) : assess(promotion,
                    context.basket(), balances, budget);
            if (assessment.status() == APPLIED) {
                DiscountCalculation calculation = assessment.calculation().orElseThrow();
                for (var entry : calculation.allocation().entrySet()) {
                    balances.put(entry.getKey(), balances.get(entry.getKey()).minus(entry.getValue()));
                }
                remaining = remaining.minus(calculation.allowed());
                budget = budget.minus(calculation.allowed());
                selected.set(positions.get(promotion.id()));
            }
            decisions.add(decision(promotion, assessment.status(), assessment.calculation(), remaining, null,
                    qualification));
        }
        List<LinePricing> lines = context.basket().lines().stream().sorted(Comparator.comparing(CartLine::sku))
                .map(line -> new LinePricing(line.sku(), line.total(), line.total().minus(balances.get(line.sku())),
                balances.get(line.sku()))).toList();
        return new PricingResult(original, original.minus(remaining), remaining, decisions, lines);
    }

    private Assessment assess(Promotion promotion, Basket basket, Map<String, Money> balances, Money budget) {
        Money remaining = Money.ZERO, base = Money.ZERO;
        var weights = new TreeMap<String, Money>();
        for (CartLine line : basket.lines()) {
            Money balance = balances.get(line.sku());
            remaining = remaining.plus(balance);
            if (promotion.scope().includes(line)) {
                base = base.plus(balance);
                weights.put(line.sku(), balance);
            }
        }
        if (remaining.cents() == 0)
            return new Assessment(NO_REMAINING_BALANCE, Optional.empty());
        if (base.cents() == 0)
            return new Assessment(NO_ELIGIBLE_BALANCE, Optional.empty());
        if (budget.cents() == 0)
            return new Assessment(LIMIT_REACHED, Optional.empty());
        Money requested = Objects.requireNonNull(promotion.discount().calculate(base),
                "Strategia rabatu zwróciła null");
        Money allowed = requested.min(base).min(budget);
        var calculation = new DiscountCalculation(base, budget, requested, allowed, new DiscountAllocator()
                .allocate(allowed, weights));
        return new Assessment(allowed.cents() == 0 ? NO_REDUCTION : APPLIED, Optional.of(calculation));
    }

    private Qualification qualify(Promotion promotion, PricingContext context, Instant now) {
        if (promotion.period().isEmpty()) {
            ConditionEvaluation trace = Objects.requireNonNull(promotion.condition().evaluate(context));
            requireEvaluated(trace);
            return new Qualification(true, trace);
        }
        CampaignPeriod period = promotion.period().orElseThrow();
        boolean active = period.contains(now);
        ConditionEvaluation periodTrace = ConditionEvaluation.leaf("PERIOD expected=[" + period.from() + "," + period.to() + "), actual=" + now, active);
        ConditionEvaluation condition = active ? Objects.requireNonNull(promotion.condition()
                .evaluate(context)) : promotion.condition().skipped();
        if (active)
            requireEvaluated(condition);
        return new Qualification(active, new ConditionEvaluation("CAMPAIGN_AND_CONDITION", active
                && condition.matched() ? ConditionEvaluation.Outcome.SATISFIED : ConditionEvaluation.Outcome.UNSATISFIED, List.of(periodTrace, condition)));
    }

    private void requireEvaluated(ConditionEvaluation evaluation) {
        if (evaluation.outcome() == ConditionEvaluation.Outcome.SKIPPED)
            throw new IllegalStateException("Oceniany warunek zwrócił SKIPPED");
    }

    private PromotionDecision decision(Promotion promotion, DecisionStatus status,
            Optional<DiscountCalculation> calculation, Money remaining, PromotionId blocker,
            Qualification qualification) {
        return new PromotionDecision(promotion.id(), status, calculation, remaining, Optional.ofNullable(blocker),
                qualification.trace());
    }
}
