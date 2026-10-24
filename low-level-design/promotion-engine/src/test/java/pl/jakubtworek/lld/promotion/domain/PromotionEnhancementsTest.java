package pl.jakubtworek.lld.promotion.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.promotion.domain.ConditionEvaluation.Outcome.*;
import static pl.jakubtworek.lld.promotion.domain.DecisionStatus.*;
import static pl.jakubtworek.lld.promotion.domain.DiscountCalculation.ClippingReason.*;

/**
 * Testy izolują różnice nowych kontraktów: pominięcie to nie false ani zero.
 */
class PromotionEnhancementsTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void andExplainsFirstFailureAndPreservesNestedSkippedBranches() {
        var calls = new AtomicInteger();
        Condition forbidden = c -> {
            calls.incrementAndGet();
            throw new AssertionError("Nie wolno oceniać pominiętej gałęzi");
        };
        var condition = Conditions.allOf(Conditions.minimumSubtotal(Money.pln("100")), Conditions.coupon("MISSING"),
                Conditions.anyOf(forbidden, Conditions.tier(CustomerTier.VIP)));
        var trace = condition.evaluate(context(10000, 0));
        assertEquals(UNSATISFIED, trace.outcome());
        assertEquals(SATISFIED, trace.children().get(0).outcome());
        assertTrue(trace.children().get(0).description().contains("actual=100.00 PLN"));
        assertEquals(UNSATISFIED, trace.children().get(1).outcome());
        assertEquals(SKIPPED, trace.children().get(2).outcome());
        assertTrue(trace.children().get(2).children().stream().allMatch(c -> c.outcome() == SKIPPED));
        assertEquals(0, calls.get());
        assertThrows(UnsupportedOperationException.class, () -> trace.children().clear());
    }

    @Test
    void orExplainsSuccessWithoutInvokingFollowingPredicate() {
        Condition forbidden = c -> {
            throw new AssertionError("Short-circuit");
        };
        var trace = Conditions.anyOf(Conditions.always(), forbidden).evaluate(context(10, 0));
        assertEquals(SATISFIED, trace.outcome());
        assertEquals(SKIPPED, trace.children().get(1).outcome());
    }

    @Test
    void customConditionIsEvaluatedOnceEvenForBestSingleReporting() {
        var calls = new AtomicInteger();
        var promo = new Promotion(new PromotionId("A"), 0, c -> {
            calls.incrementAndGet();
            return true;
        }, new FixedDiscount(new Money(1)), StackingPolicy.STACKABLE, Set.of());
        var result = engine(List.of(promo), 100, new BestSingleSelection()).quote(context(100, 0));
        assertEquals(1, calls.get());
        assertEquals("CUSTOM", result.decisions().getFirst().qualification().description());
    }

    @Test
    void notCalculatedAndCalculatedZeroAreDifferentValues() {
        var missing = new Promotion(new PromotionId("A"), 20, Conditions.coupon("NO"), new FixedDiscount(new Money(1)),
                StackingPolicy.STACKABLE, Set.of());
        var tiny = percentage("B", 10, 1);
        var result = engine(List.of(missing, tiny), 10, new PrioritySelection()).quote(context(1, 0));
        assertTrue(result.decisions().get(0).requested().isEmpty());
        assertTrue(result.decisions().get(1).requested().isPresent());
        assertEquals(Money.ZERO, result.decisions().get(1).requested().orElseThrow());
        assertEquals(NO_REDUCTION, result.decisions().get(1).status());
    }

    @ParameterizedTest
    @CsvSource({ "10,25,20,ELIGIBLE_BALANCE", "100,10,20,DISCOUNT_LIMIT" })
    void clippingNamesTheExceededConstraint(long subtotal, long cap, long requested,
            DiscountCalculation.ClippingReason reason) {
        var result = engine(List.of(fixed("A", 0, requested)), cap, new PrioritySelection()).quote(context(subtotal,
                0));
        assertEquals(Set.of(reason), result.decisions().getFirst().calculation().orElseThrow().clippingReasons());
    }

    @Test
    void bothConstraintsCanBeExceededAndUnlimitedCapIsNotMislabelled() {
        var result = engine(List.of(fixed("A", 0, 20)), 5, new PrioritySelection()).quote(context(10, 0));
        assertEquals(Set.of(ELIGIBLE_BALANCE, DISCOUNT_LIMIT), result.decisions().getFirst().calculation().orElseThrow()
                .clippingReasons());
        var unlimited = new PromotionEngine(List.of(fixed("A", 0, 20))).quote(context(10, 0));
        assertEquals(Set.of(ELIGIBLE_BALANCE), unlimited.decisions().getFirst().calculation().orElseThrow()
                .clippingReasons());
    }

    @Test
    void categoryBaseAndFollowingGlobalDiscountUseRemainingLineBalances() {
        var books = percentage("A", 20, 5000).scopedTo(DiscountScope.category("books"));
        var all = percentage("B", 10, 5000);
        var result = engine(List.of(books, all), 10000, new PrioritySelection()).quote(context(4000, 6000));
        assertEquals(new Money(4000), result.payable());
        assertEquals(new Money(4000), result.decisions().get(0).calculation().orElseThrow().base());
        assertEquals(new Money(8000), result.decisions().get(1).calculation().orElseThrow().base());
        assertEquals(new Money(1000), result.lines().get(0).payable());
        assertEquals(new Money(3000), result.lines().get(1).payable());
    }

    @Test
    void emptyCategoryBaseDoesNotActivateExclusiveOrConsumeBudget() {
        var missing = new Promotion(new PromotionId("A"), 20, Conditions.always(), new FixedDiscount(new Money(100)),
                StackingPolicy.EXCLUSIVE, Set.of()).scopedTo(DiscountScope.category("missing"));
        var result = engine(List.of(missing, fixed("B", 10, 20)), 50, new PrioritySelection()).quote(context(100, 0));
        assertEquals(NO_ELIGIBLE_BALANCE, result.decisions().getFirst().status());
        assertEquals(APPLIED, result.decisions().get(1).status());
        assertEquals(new Money(80), result.payable());
    }

    @Test
    void largestRemaindersUseSkuForTiesAndIgnoreInputOrder() {
        var allocator = new DiscountAllocator();
        var weights = Map.of("C", new Money(1), "A", new Money(1), "B", new Money(1));
        assertEquals(Map.of("A", new Money(1), "B", new Money(1)), allocator.allocate(new Money(2), weights));
        var result = allocator.allocate(new Money(2), weights);
        assertThrows(UnsupportedOperationException.class, result::clear);
        assertThrows(IllegalArgumentException.class, () -> allocator.allocate(new Money(4), weights));
        assertEquals(Map.of(), allocator.allocate(Money.ZERO, Map.of()));
    }

    @Test
    void allocatorHandlesMaximumIntermediateWithoutOverflow() {
        var weights = Map.of("A", new Money(Long.MAX_VALUE - 1), "B", new Money(1));
        assertEquals(weights, new DiscountAllocator().allocate(new Money(Long.MAX_VALUE), weights));
    }

    @Test
    void categoryCapAllocatesOnlyToEligibleLinesAndDoesNotOverdraw() {
        var promo = fixed("A", 0, 1000).scopedTo(DiscountScope.category("books"));
        var context = new PricingContext(new Basket(List.of(new CartLine("A", "books", new Money(1), 1),
                new CartLine("B", "books", new Money(1), 1), new CartLine("C", "tech", new Money(98), 1))),
                CustomerTier.STANDARD, Set.of());
        var result = engine(List.of(promo), 1, new PrioritySelection()).quote(context);
        assertEquals(Map.of("A", new Money(1)), result.decisions().getFirst().calculation().orElseThrow().allocation());
        assertEquals(new Money(98), result.lines().get(2).payable());
        assertEquals(new Money(99), result.payable());
    }

    @ParameterizedTest
    @CsvSource({ "-1,NOT_ACTIVE", "0,APPLIED", "9,APPLIED", "10,NOT_ACTIVE" })
    void campaignUsesHalfOpenInterval(int seconds, DecisionStatus expected) {
        var promo = fixed("A", 0, 1).during(new CampaignPeriod(NOW, NOW.plusSeconds(10)));
        var engine = new PromotionEngine(List.of(promo), new Money(100), Clock.fixed(NOW.plusSeconds(seconds),
                ZoneOffset.UTC), new PrioritySelection());
        var result = engine.quote(context(100, 0));
        assertEquals(expected, result.decisions().getFirst().status());
        if (expected == NOT_ACTIVE)
            assertEquals(SKIPPED, result.decisions().getFirst().qualification().children().get(1).outcome());
    }

    @Test
    void clockIsReadOnceForMultipleCampaignsAndInactiveConditionsAreNotInvoked() {
        var reads = new AtomicInteger();
        Clock clock = new Clock() {

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return NOW.plusSeconds(reads.getAndIncrement() * 100L);
            }
        };
        CampaignPeriod period = new CampaignPeriod(NOW, NOW.plusSeconds(1));
        var result = new PromotionEngine(List.of(fixed("A", 20, 1).during(period), fixed("B", 10, 1).during(period)),
                new Money(100), clock, new PrioritySelection()).quote(context(100, 0));
        assertEquals(1, reads.get());
        assertEquals(2, result.discount().cents());
        var inactive = new Promotion(new PromotionId("A"), 0, c -> {
            throw new AssertionError("Poza okresem");
        }, new FixedDiscount(new Money(1)), StackingPolicy.STACKABLE, Set.of())
                .during(new CampaignPeriod(NOW.minusSeconds(2), NOW));
        assertEquals(NOT_ACTIVE, engine(List.of(inactive), 100, new PrioritySelection()).quote(context(100, 0))
                .decisions().getFirst().status());
    }

    @Test
    void invalidPeriodsAreRejectedAndInstantDoesNotDependOnClockZone() {
        assertThrows(IllegalArgumentException.class, () -> new CampaignPeriod(NOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> new CampaignPeriod(NOW, NOW.minusSeconds(1)));
        var promo = fixed("A", 0, 1).during(new CampaignPeriod(NOW, NOW.plusSeconds(10)));
        var utc = new PromotionEngine(List.of(promo), new Money(100), CLOCK, new PrioritySelection()).quote(context(100,
                0));
        var tokyo = new PromotionEngine(List.of(promo), new Money(100), CLOCK.withZone(ZoneId.of("Asia/Tokyo")),
                new PrioritySelection()).quote(context(100, 0));
        assertEquals(utc, tokyo);
    }

    @Test
    void bestSingleChoosesAfterClippingWithPriorityAndIdTieBreak() {
        Promotion a = fixed("A", 10, 20), b = fixed("B", 20, 30), c = fixed("C", 20, 40);
        var result = engine(List.of(c, a, b), 10, new BestSingleSelection()).quote(context(100, 0));
        assertEquals(new PromotionId("B"), result.decisions().stream().filter(d -> d.status() == APPLIED).findFirst()
                .orElseThrow().id());
        assertEquals(new Money(10), result.discount());
        assertEquals(NOT_SELECTED, result.decisions().get(1).status());
        assertEquals(Money.ZERO, result.decisions().get(1).applied());
        assertTrue(result.decisions().get(1).calculation().orElseThrow().allowed().cents() > 0);
    }

    @Test
    void bestSingleAndPriorityAreDifferentPoliciesNotAnOptimizerOfCombinations() {
        Promotion a = fixed("A", 20, 10), b = fixed("B", 10, 20);
        assertEquals(new Money(30), engine(List.of(a, b), 100, new PrioritySelection()).quote(context(100, 0))
                .discount());
        assertEquals(new Money(20), engine(List.of(a, b), 100, new BestSingleSelection()).quote(context(100, 0))
                .discount());
    }

    @Test
    void bestSingleDoesNotRunWinningDiscountTwiceAndRejectsInvalidPolicyIds() {
        var calls = new AtomicInteger();
        var promo = new Promotion(new PromotionId("A"), 0, Conditions.always(), base -> {
            calls.incrementAndGet();
            return new Money(1);
        }, StackingPolicy.STACKABLE, Set.of());
        engine(List.of(promo), 100, new BestSingleSelection()).quote(context(100, 0));
        assertEquals(1, calls.get());
        SelectionPolicy invalid = (ordered, benefit) -> Set.of(new PromotionId("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> engine(List.of(promo), 100, invalid).quote(context(100, 0)));
    }

    @Test
    void reportRejectsUnknownAllocationSkuAndKeepsItsCollectionsImmutable() {
        var calculation = new DiscountCalculation(new Money(2), new Money(2), new Money(1), new Money(1), Map.of("A",
                new Money(1)));
        var decision = new PromotionDecision(new PromotionId("A"), APPLIED, java.util.Optional.of(calculation),
                new Money(1), java.util.Optional.empty(), ConditionEvaluation.leaf("ALWAYS", true));
        var lines = List.of(new LinePricing("A", new Money(2), new Money(1), new Money(1)));
        var report = new PricingResult(new Money(2), new Money(1), new Money(1), List.of(decision), lines);
        assertThrows(UnsupportedOperationException.class, () -> report.lines().clear());
        assertThrows(UnsupportedOperationException.class, () -> calculation.allocation().clear());
        assertThrows(IllegalArgumentException.class, () -> new PricingResult(new Money(2), new Money(1), new Money(1),
                List.of(decision), List.of(new LinePricing("B", new Money(2), new Money(1), new Money(1)))));
    }

    @Test
    void reportRejectsAllocatedAmountWhichWouldOverdrawOneLine() {
        var calculation = new DiscountCalculation(new Money(2), new Money(2), new Money(2), new Money(2), Map.of("A",
                new Money(2)));
        var decision = new PromotionDecision(new PromotionId("A"), APPLIED, java.util.Optional.of(calculation),
                new Money(1), java.util.Optional.empty(), ConditionEvaluation.leaf("ALWAYS", true));
        assertThrows(IllegalArgumentException.class, () -> new PricingResult(new Money(3), new Money(2), new Money(1),
                List.of(decision), List.of(new LinePricing("A", new Money(1), new Money(1), Money.ZERO),
                new LinePricing("B", new Money(2), new Money(1), new Money(1)))));
    }

    private static PromotionEngine engine(List<Promotion> promotions, long cap, SelectionPolicy selection) {
        return new PromotionEngine(promotions, new Money(cap), CLOCK, selection);
    }

    private static PricingContext context(long books, long tech) {
        return new PricingContext(new Basket(List.of(new CartLine("A", "books", new Money(books), 1), new CartLine("B",
                "tech", new Money(tech), 1))), CustomerTier.STANDARD, Set.of());
    }

    private static Promotion fixed(String id, int priority, long amount) {
        return new Promotion(new PromotionId(id), priority, Conditions.always(), new FixedDiscount(new Money(amount)),
                StackingPolicy.STACKABLE, Set.of());
    }

    private static Promotion percentage(String id, int priority, int rate) {
        return new Promotion(new PromotionId(id), priority, Conditions.always(),
                new PercentageDiscount(new Percent(rate)), StackingPolicy.STACKABLE, Set.of());
    }
}
