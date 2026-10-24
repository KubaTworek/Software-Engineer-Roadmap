package pl.jakubtworek.lld.promotion.console;

import pl.jakubtworek.lld.promotion.domain.*;
import java.util.List;
import java.util.Set;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Jawna konfiguracja dydaktyczna, nie repozytorium kampanii. WELCOME15 deklaruje
 * wykluczenie BOOKS20 tylko po jednej stronie, by demo pokazało symetrię reguły.
 */
public final class DemoPromotions {

    private DemoPromotions() {
    }

    public static List<Promotion> all() {
        return List.of(new Promotion(new PromotionId("FLASH50"), 200, Conditions.coupon("FLASH50"),
                new PercentageDiscount(Percent.of("50")), StackingPolicy.EXCLUSIVE, Set.of()),
                new Promotion(new PromotionId("VIP10"), 100, Conditions.tier(CustomerTier.VIP),
                new PercentageDiscount(Percent.of("10")), StackingPolicy.STACKABLE, Set.of()),
                new Promotion(new PromotionId("WELCOME15"), 90, Conditions.allOf(Conditions.coupon("WELCOME"),
                Conditions.minimumSubtotal(Money.pln("50"))), new FixedDiscount(Money.pln("15")),
                StackingPolicy.STACKABLE, Set.of(new PromotionId("BOOKS20"))), new Promotion(new PromotionId("BOOKS20"),
                80, Conditions.allOf(Conditions.category("books"), Conditions.minimumSubtotal(Money.pln("100"))),
                new FixedDiscount(Money.pln("20")), StackingPolicy.STACKABLE, Set.of()),
                new Promotion(new PromotionId("BOOKS10"), 70, Conditions.coupon("BOOKS10"),
                new PercentageDiscount(Percent.of("10")), StackingPolicy.STACKABLE, Set.of())
                .scopedTo(DiscountScope.category("books")), new Promotion(new PromotionId("VALID5"), 60,
                Conditions.coupon("VALID5"), new FixedDiscount(Money.pln("5")), StackingPolicy.STACKABLE, Set.of())
                .during(new CampaignPeriod(Instant.parse("2026-10-21T00:00:00Z"),
                Instant.parse("2026-10-22T00:00:00Z"))));
    }

    public static PromotionEngine engine() {
        return new PromotionEngine(all(), Money.pln("25"), Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"),
                ZoneOffset.UTC), new PrioritySelection());
    }
}
