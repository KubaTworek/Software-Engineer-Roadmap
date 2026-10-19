package pl.jakubtworek.backend_engineering.stage_1.block_e.semantic_trap;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Shorter implementation that accidentally moves the monetary rounding boundary. */
public final class CleanButSemanticallyDifferentDiscountCalculator {

    public BigDecimal totalAfterDiscount(List<OrderLine> lines, BigDecimal discountRate) {
        BigDecimal subtotal = lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return subtotal.multiply(BigDecimal.ONE.subtract(discountRate))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
