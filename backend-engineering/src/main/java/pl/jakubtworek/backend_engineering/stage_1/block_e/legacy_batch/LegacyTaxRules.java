package pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch;

import java.math.BigDecimal;

/** A static business dependency that would normally come from configuration or a policy service. */
public final class LegacyTaxRules {

    private LegacyTaxRules() {
    }

    public static BigDecimal rateFor(String country) {
        return switch (country) {
            case "PL" -> new BigDecimal("0.23");
            case "DE" -> new BigDecimal("0.19");
            default -> new BigDecimal("0.20");
        };
    }
}
