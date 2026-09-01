package pl.jakubtworek.backend_engineering.stage_1.block_e.migration;

import java.math.BigDecimal;

@FunctionalInterface
public interface TaxPolicy {

    BigDecimal rateFor(String country);
}
