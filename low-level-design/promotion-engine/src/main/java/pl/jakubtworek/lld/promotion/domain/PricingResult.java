package pl.jakubtworek.lld.promotion.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Raport chroni bilans globalny i każdą linię. Tylko APPLIED odejmuje alokacje:
 * potencjalny rabat przegranej oferty BEST_SINGLE nie jest kosztem koszyka.
 * Konstruktor wykrywa obce SKU, ujemne saldo i rozbieżne sumy obu poziomów.
 */
public record PricingResult(Money subtotal, Money discount, Money payable, List<PromotionDecision> decisions,
        List<LinePricing> lines) {

    public PricingResult {
        Objects.requireNonNull(subtotal);
        Objects.requireNonNull(discount);
        Objects.requireNonNull(payable);
        decisions = List.copyOf(decisions);
        lines = List.copyOf(lines);
        if (!subtotal.minus(discount).equals(payable))
            throw new IllegalArgumentException("Niespójny bilans wyceny");
        var balances = new TreeMap<String, Money>();
        Money originalSum = Money.ZERO;
        for (LinePricing line : lines) {
            if (balances.put(line.sku(), line.original()) != null)
                throw new IllegalArgumentException("Powtórzone SKU raportu");
            originalSum = originalSum.plus(line.original());
        }
        if (!originalSum.equals(subtotal))
            throw new IllegalArgumentException("Linie nie wyjaśniają sumy koszyka");
        var ids = new HashSet<PromotionId>();
        Money balance = subtotal;
        for (PromotionDecision decision : decisions) {
            if (!ids.add(decision.id()))
                throw new IllegalArgumentException("Powtórzone ID w raporcie");
            if (decision.status() == DecisionStatus.APPLIED) {
                for (var entry : decision.calculation().orElseThrow().allocation().entrySet()) {
                    Money before = balances.get(entry.getKey());
                    if (before == null)
                        throw new IllegalArgumentException("Obce SKU w alokacji");
                    balances.put(entry.getKey(), before.minus(entry.getValue()));
                }
            }
            balance = balance.minus(decision.applied());
            if (!balance.equals(decision.remainingAfter()))
                throw new IllegalArgumentException("Niespójne saldo kroku");
        }
        if (!balance.equals(payable))
            throw new IllegalArgumentException("Decyzje nie wyjaśniają należności");
        Money finalSum = Money.ZERO;
        for (LinePricing line : lines) {
            if (!line.payable().equals(balances.get(line.sku())))
                throw new IllegalArgumentException("Rozbieżna alokacja linii");
            finalSum = finalSum.plus(line.payable());
        }
        if (!finalSum.equals(payable))
            throw new IllegalArgumentException("Linie nie wyjaśniają należności");
    }
}
