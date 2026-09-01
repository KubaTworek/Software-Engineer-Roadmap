package pl.jakubtworek.backend_engineering.stage_1.block_e.semantic_trap;

import java.math.BigDecimal;

public record OrderLine(BigDecimal unitPrice, int quantity) {
}
