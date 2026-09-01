package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import java.math.BigDecimal;

/**
 * Domain failure: retrying the same command cannot make an insufficient balance sufficient.
 */
public final class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(Long accountId, BigDecimal balance, BigDecimal amount) {
        super("Account %s has balance %s, requested withdrawal is %s"
                .formatted(accountId, balance, amount));
    }
}
