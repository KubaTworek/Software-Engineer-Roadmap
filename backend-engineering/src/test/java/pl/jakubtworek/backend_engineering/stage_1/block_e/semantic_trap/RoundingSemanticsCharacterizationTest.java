package pl.jakubtworek.backend_engineering.stage_1.block_e.semantic_trap;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoundingSemanticsCharacterizationTest {

    @Test
    void cleanerAggregationAccidentallyChangesTheMonetaryContract() {
        List<OrderLine> threeSmallLines = List.of(
                new OrderLine(new BigDecimal("0.05"), 1),
                new OrderLine(new BigDecimal("0.05"), 1),
                new OrderLine(new BigDecimal("0.05"), 1));
        BigDecimal discount = new BigDecimal("0.10");

        BigDecimal legacy = new LegacyLineByLineDiscountCalculator()
                .totalAfterDiscount(threeSmallLines, discount);
        BigDecimal cleaner = new CleanButSemanticallyDifferentDiscountCalculator()
                .totalAfterDiscount(threeSmallLines, discount);

        assertThat(legacy).isEqualByComparingTo("0.15");
        assertThat(cleaner).isEqualByComparingTo("0.14");
        assertThat(cleaner).as("moving the rounding boundary is a behavior change, not a refactoring")
                .isNotEqualByComparingTo(legacy);
    }
}
