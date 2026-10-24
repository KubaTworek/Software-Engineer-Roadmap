package pl.jakubtworek.lld.atm;

import java.util.Objects;

/**
 * Stabilny kod odmowy domenowej oddziela brak salda od niemożliwego zestawu nominałów i błędu sesji.
 */
public final class AtmException extends RuntimeException {

    public enum Reason {

        INVALID_SESSION_STATE,
        STALE_SESSION,
        SESSION_EXPIRED,
        IDEMPOTENCY_CONFLICT,
        UNKNOWN_CARD,
        INVALID_AMOUNT,
        AMOUNT_LIMIT_EXCEEDED,
        INSUFFICIENT_FUNDS,
        INSUFFICIENT_CASH,
        CANNOT_DISPENSE
    }

    private final Reason reason;

    public AtmException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
