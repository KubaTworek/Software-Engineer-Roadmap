package pl.jakubtworek.lld.promotion.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.promotion.domain.DecisionStatus.*;
import static pl.jakubtworek.lld.promotion.domain.StackingPolicy.*;

/**
 * Testy obserwują cały raport i należność. Małe przykłady odróżniają politykę
 * biznesową od poszukiwania maksimum; generator z ustalonym ziarnem sprawdza
 * arytmetykę i niezależność od kolejności konfiguracji na skończonej próbce.
 */
class PromotionEngineTest {

    @Test
    void sequentialPercentagesUseRemainingBalance() {
        var result = new PromotionEngine(List.of(rate("A", 20, "10"), rate("B", 10, "10"))).quote(context("100"));
        assertEquals(Money.pln("81"), result.payable());
        assertEquals(List.of(Money.pln("10"), Money.pln("9")), result.decisions().stream()
                .map(PromotionDecision::applied).toList());
    }

    @Test
    void priorityOfFixedAndPercentageChangesPrice() {
        var percentFirst = new PromotionEngine(List.of(rate("RATE", 20, "10"), fixed("FIXED", 10, "20")))
                .quote(context("100"));
        var fixedFirst = new PromotionEngine(List.of(rate("RATE", 10, "10"), fixed("FIXED", 20, "20")))
                .quote(context("100"));
        assertEquals(Money.pln("70"), percentFirst.payable());
        assertEquals(Money.pln("72"), fixedFirst.payable());
    }

    @Test
    void equalPriorityUsesIdAndIgnoresConfigurationOrder() {
        Promotion a = fixed("A", Integer.MAX_VALUE, "20"), b = rate("B", Integer.MAX_VALUE, "10");
        var first = new PromotionEngine(List.of(b, a)).quote(context("100"));
        assertEquals(new PromotionEngine(List.of(a, b)).quote(context("100")), first);
        assertEquals(Money.pln("72"), first.payable());
        assertEquals(List.of(new PromotionId("A"), new PromotionId("B")), first.decisions().stream()
                .map(PromotionDecision::id).toList());
    }

    @Test
    void globalCapPartiallyAppliesLastPromotion() {
        var result = new PromotionEngine(List.of(rate("A", 20, "10"), fixed("B", 10, "20"), fixed("C", 0, "5")),
                Money.pln("25")).quote(context("100"));
        assertEquals(Money.pln("75"), result.payable());
        assertEquals(Money.pln("20"), result.decisions().get(1).requested().orElseThrow());
        assertEquals(Money.pln("15"), result.decisions().get(1).applied());
        assertEquals(LIMIT_REACHED, result.decisions().get(2).status());
    }

    @Test
    void noCapConstructorStillCannotDiscountBelowZero() {
        var result = new PromotionEngine(List.of(fixed("A", 20, "200"), fixed("B", 10, "1"))).quote(context("100"));
        assertEquals(Money.ZERO, result.payable());
        assertEquals(Money.pln("100"), result.discount());
        assertEquals(NO_REMAINING_BALANCE, result.decisions().get(1).status());
    }

    @Test
    void fullPercentageOfMaximumSubtotalPreservesBalanceWithoutOverflow() {
        var context = new PricingContext(new Basket(List.of(new CartLine("A", "tech", new Money(Long.MAX_VALUE), 1))),
                CustomerTier.VIP, Set.of());
        var result = new PromotionEngine(List.of(rate("A", 0, "100"))).quote(context);
        assertEquals(new Money(Long.MAX_VALUE), result.discount());
        assertEquals(Money.ZERO, result.payable());
    }

    @Test
    void zeroCapAndEmptyBasketHaveDistinctExplanations() {
        var engine = new PromotionEngine(List.of(fixed("A", 0, "10")), Money.ZERO);
        assertEquals(LIMIT_REACHED, engine.quote(context("100")).decisions().getFirst().status());
        var empty = new PricingContext(new Basket(List.of()), CustomerTier.STANDARD, Set.of());
        assertEquals(NO_REMAINING_BALANCE, engine.quote(empty).decisions().getFirst().status());
    }

    @Test
    void exclusivePriorityWinsEvenWhenAnotherDiscountWouldBeLarger() {
        var exclusive = promotion("A", 20, Conditions.always(), new FixedDiscount(Money.pln("1")), EXCLUSIVE, Set.of());
        var result = new PromotionEngine(List.of(exclusive, fixed("B", 10, "50"))).quote(context("100"));
        assertEquals(Money.pln("99"), result.payable());
        assertEquals(BLOCKED_EXCLUSIVE, result.decisions().get(1).status());
        assertEquals(Optional.of(new PromotionId("A")), result.decisions().get(1).blocker());
    }

    @Test
    void lowerPriorityExclusiveCannotReplaceAlreadyAppliedPromotion() {
        var low = promotion("B", 10, Conditions.always(), new FixedDiscount(Money.pln("50")), EXCLUSIVE, Set.of());
        var result = new PromotionEngine(List.of(fixed("A", 20, "10"), low)).quote(context("100"));
        assertEquals(Money.pln("90"), result.payable());
        assertEquals(BLOCKED_EXCLUSIVE, result.decisions().get(1).status());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void oneSidedExclusionBlocksInBothPriorityDirections(boolean aFirst) {
        var a = promotion("A", aFirst ? 20 : 0, Conditions.always(), new FixedDiscount(Money.pln("10")), STACKABLE,
                Set.of(new PromotionId("B")));
        var b = fixed("B", 10, "20");
        var result = new PromotionEngine(List.of(a, b)).quote(context("100"));
        assertEquals(EXCLUDED, result.decisions().get(1).status());
        assertEquals(Optional.of(new PromotionId(aFirst ? "A" : "B")), result.decisions().get(1).blocker());
        assertEquals(Money.pln(aFirst ? "90" : "80"), result.payable());
    }

    @Test
    void unqualifiedPromotionDoesNotBlockAndDoesNotRunDiscount() {
        var skipped = promotion("A", 20, Conditions.coupon("MISSING"), remaining -> {
            throw new AssertionError("Niekwalifikowany rabat nie powinien być obliczany");
        }, EXCLUSIVE, Set.of(new PromotionId("B")));
        var result = new PromotionEngine(List.of(skipped, fixed("B", 10, "20"))).quote(context("100"));
        assertEquals(CONDITION_NOT_MET, result.decisions().getFirst().status());
        assertEquals(Money.pln("80"), result.payable());
    }

    @Test
    void zeroRoundedExclusiveDoesNotConsumeCompatibility() {
        var noReduction = promotion("A", 20, Conditions.always(), new PercentageDiscount(new Percent(1)), EXCLUSIVE,
                Set.of(new PromotionId("B")));
        var result = new PromotionEngine(List.of(noReduction, fixed("B", 10, "0.01"))).quote(context("0.01"));
        assertEquals(NO_REDUCTION, result.decisions().getFirst().status());
        assertEquals(APPLIED, result.decisions().get(1).status());
        assertEquals(Money.ZERO, result.payable());
    }

    @Test
    void thresholdUsesOriginalBasketAfterEarlierDiscount() {
        var threshold = promotion("B", 10, Conditions.minimumSubtotal(Money.pln("100")),
                new FixedDiscount(Money.pln("10")), STACKABLE, Set.of());
        var result = new PromotionEngine(List.of(fixed("A", 20, "30"), threshold)).quote(context("100"));
        assertEquals(Money.pln("60"), result.payable());
        assertEquals(APPLIED, result.decisions().get(1).status());
    }

    @Test
    void conditionPrecedesCompatibilityAndCompatibilityPrecedesBudget() {
        var exclusive = promotion("A", 30, Conditions.always(), new FixedDiscount(Money.pln("1")), EXCLUSIVE, Set.of());
        var falseCondition = promotion("B", 20, Conditions.coupon("MISSING"), new FixedDiscount(Money.pln("1")),
                STACKABLE, Set.of());
        var result = new PromotionEngine(List.of(exclusive, falseCondition, fixed("C", 10, "1")), Money.pln("1"))
                .quote(context("10"));
        assertEquals(CONDITION_NOT_MET, result.decisions().get(1).status());
        assertEquals(BLOCKED_EXCLUSIVE, result.decisions().get(2).status());
    }

    @Test
    void categoryOnlyQualifiesAndDoesNotRestrictDiscountBaseToThatCategory() {
        var context = new PricingContext(new Basket(List.of(new CartLine("A", "books", Money.pln("1"), 1),
                new CartLine("B", "tech", Money.pln("99"), 1))), CustomerTier.STANDARD, Set.of());
        var promo = promotion("A", 0, Conditions.category("books"), new PercentageDiscount(Percent.of("10")), STACKABLE,
                Set.of());
        assertEquals(Money.pln("90"), new PromotionEngine(List.of(promo)).quote(context).payable());
    }

    @Test
    void invalidConfigurationFailsBeforeEvaluation() {
        Promotion a = fixed("A", 0, "1");
        assertThrows(IllegalArgumentException.class, () -> new PromotionEngine(List.of(a, a)));
        var unknown = promotion("A", 0, Conditions.always(), new FixedDiscount(Money.pln("1")), STACKABLE,
                Set.of(new PromotionId("UNKNOWN")));
        assertThrows(IllegalArgumentException.class, () -> new PromotionEngine(List.of(unknown)));
        assertThrows(IllegalArgumentException.class, () -> promotion("A", -1, Conditions.always(),
                new FixedDiscount(Money.pln("1")), STACKABLE, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> promotion("A", 0, Conditions.always(),
                new FixedDiscount(Money.pln("1")), STACKABLE, Set.of(new PromotionId("A"))));
    }

    @Test
    void engineAndResultDoNotExposeMutableCollections() {
        var input = new ArrayList<>(List.of(fixed("A", 0, "10")));
        var engine = new PromotionEngine(input);
        input.clear();
        var context = context("100");
        PricingResult result = engine.quote(context);
        assertEquals(Money.pln("90"), result.payable());
        assertEquals(result, engine.quote(context));
        assertEquals(Money.pln("100"), context.basket().subtotal());
        assertThrows(UnsupportedOperationException.class, () -> result.decisions().clear());
        assertThrows(UnsupportedOperationException.class, () -> fixed("A", 0, "1").excludedIds().clear());
    }

    @Test
    void brokenExtensionFailsWithoutReturningPartialQuote() {
        var context = context("100");
        var nullDiscount = promotion("B", 10, Conditions.always(), remaining -> null, STACKABLE, Set.of());
        var engine = new PromotionEngine(List.of(fixed("A", 20, "10"), nullDiscount));
        assertThrows(NullPointerException.class, () -> engine.quote(context));
        assertEquals(Money.pln("100"), context.basket().subtotal());
        var throwing = promotion("A", 0, c -> {
            throw new IllegalStateException("Błąd rozszerzenia");
        }, new FixedDiscount(Money.pln("1")), STACKABLE, Set.of());
        assertThrows(IllegalStateException.class, () -> new PromotionEngine(List.of(throwing)).quote(context));
    }

    @Test
    void reportConstructorsRejectContradictoryBalancesAndStatuses() {
        var id = new PromotionId("A");
        var qualification = ConditionEvaluation.leaf("ALWAYS", true);
        assertThrows(IllegalArgumentException.class, () -> new PromotionDecision(id, APPLIED, Optional.empty(),
                Money.pln("100"), Optional.empty(), qualification));
        assertThrows(IllegalArgumentException.class, () -> new PromotionDecision(id, EXCLUDED, Optional.empty(),
                Money.pln("100"), Optional.empty(), qualification));
        assertThrows(IllegalArgumentException.class, () -> new PricingResult(Money.pln("100"), Money.pln("10"),
                Money.pln("80"), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PricingResult(Money.pln("100"), Money.pln("10"),
                Money.pln("90"), List.of(), List.of()));
    }

    @Test
    void noPromotionsMeansUnchangedPriceAndEmptyTrace() {
        var result = new PromotionEngine(List.of()).quote(context("100"));
        assertEquals(Money.pln("100"), result.payable());
        assertEquals(Money.ZERO, result.discount());
        assertTrue(result.decisions().isEmpty());
    }

    @Test
    void generatedQuotesMatchDecimalOracleAndArePermutationInvariant() {
        var random = new Random(110021L);
        // 1000 scenariuszy, maks. 8 promocji: oracle świadomie obejmuje tylko
        // STACKABLE bez wykluczeń. Zgodność relacji sprawdzają osobne kontrprzykłady.
        for (int scenario = 0; scenario < 1000; scenario++) {
            long original = random.nextInt(100001), cap = random.nextInt(100001);
            int count = random.nextInt(9);
            var promotions = new ArrayList<Promotion>();
            long remaining = original, budget = Math.min(cap, original);
            for (int i = 0; i < count; i++) {
                boolean percent = random.nextBoolean();
                int parameter = random.nextInt(10000) + 1;
                Discount discount = percent ? new PercentageDiscount(new Percent(parameter)) : new FixedDiscount(new Money(parameter));
                promotions.add(promotion("P" + i, count - i, Conditions.always(), discount, STACKABLE, Set.of()));
                long requested = percent ? BigDecimal.valueOf(remaining).multiply(BigDecimal.valueOf(parameter))
                        .divide(BigDecimal.valueOf(10000), 0, RoundingMode.HALF_UP).longValueExact() : parameter;
                long applied = Math.min(requested, Math.min(remaining, budget));
                remaining -= applied;
                budget -= applied;
            }
            var context = new PricingContext(new Basket(List.of(new CartLine("A", "books", new Money(original), 1))),
                    CustomerTier.STANDARD, Set.of());
            var expected = new PromotionEngine(promotions, new Money(cap)).quote(context);
            assertEquals(new Money(remaining), expected.payable(), "Scenariusz " + scenario);
            assertEquals(new Money(original), expected.discount().plus(expected.payable()));
            assertTrue(expected.discount().cents() <= cap);
            Collections.shuffle(promotions, random);
            assertEquals(expected, new PromotionEngine(promotions, new Money(cap)).quote(context));
        }
    }

    private static PricingContext context(String subtotal) {
        return new PricingContext(new Basket(List.of(new CartLine("A", "books", Money.pln(subtotal), 1))),
                CustomerTier.STANDARD, Set.of());
    }

    private static Promotion fixed(String id, int priority, String amount) {
        return promotion(id, priority, Conditions.always(), new FixedDiscount(Money.pln(amount)), STACKABLE, Set.of());
    }

    private static Promotion rate(String id, int priority, String percent) {
        return promotion(id, priority, Conditions.always(), new PercentageDiscount(Percent.of(percent)), STACKABLE,
                Set.of());
    }

    private static Promotion promotion(String id, int priority, Condition condition, Discount discount,
            StackingPolicy stacking, Set<PromotionId> excluded) {
        return new Promotion(new PromotionId(id), priority, condition, discount, stacking, excluded);
    }
}
