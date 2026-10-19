package pl.jakubtworek.backend_engineering.stage_1.block_e.semantic_trap;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Existing contract rounds the discounted value of every line before summing. */
public final class LegacyLineByLineDiscountCalculator {

    public BigDecimal totalAfterDiscount(List<OrderLine> lines, BigDecimal discountRate) {
        BigDecimal multiplier = BigDecimal.ONE.subtract(discountRate);
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine line : lines) {
            BigDecimal discountedLine = line.unitPrice()
                    .multiply(BigDecimal.valueOf(line.quantity()))
                    .multiply(multiplier)
                    .setScale(2, RoundingMode.HALF_UP);
            total = total.add(discountedLine);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
