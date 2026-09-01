package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import java.math.BigDecimal;

/** Immutable result returned instead of leaking managed JPA entities outside the transaction. */
public record TransferResult(
        Long fromAccountId,
        Long toAccountId,
        BigDecimal fromBalance,
        BigDecimal toBalance
) {
}
