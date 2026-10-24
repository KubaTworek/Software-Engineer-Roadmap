package pl.jakubtworek.lld.splitwise;

import java.util.Objects;

/**
 * Kody odmów są stabilne; komunikat po polsku wyjaśnia warunek użytkownikowi demo.
 */
public final class SplitException extends RuntimeException {

    public enum Reason {

        UNKNOWN_MEMBER,
        INVALID_AMOUNT,
        SELF_SETTLEMENT,
        INVALID_SETTLEMENT_DIRECTION,
        OVERPAYMENT,
        IDEMPOTENCY_CONFLICT,
        UNKNOWN_ENTRY,
        NOT_EXPENSE,
        NOT_PAYER,
        ALREADY_REVERSED
    }

    private final Reason reason;

    public SplitException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
