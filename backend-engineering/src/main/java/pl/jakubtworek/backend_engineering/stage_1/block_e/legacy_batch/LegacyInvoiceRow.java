package pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch;

import java.math.BigDecimal;

public record LegacyInvoiceRow(String customer, String country, BigDecimal netAmount) {
}
