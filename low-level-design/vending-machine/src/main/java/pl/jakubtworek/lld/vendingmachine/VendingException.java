package pl.jakubtworek.lld.vendingmachine;

import java.util.Objects;

/**
 * Odmowa domenowa jest obserwowalnym wynikiem polecenia, nie częściowym sukcesem.
 * Kod służy klientowi i testom, komunikat czytelnikowi. Nie rozpoznajemy
 * błędu po treści tekstu i nie zamieniamy awarii programu w udany zakup.
 */
public final class VendingException extends RuntimeException {

    public enum Reason {

        NO_ACTIVE_PURCHASE,
        PURCHASE_IN_PROGRESS,
        UNKNOWN_SLOT,
        OUT_OF_STOCK,
        INSUFFICIENT_FUNDS,
        NO_EXACT_CHANGE,
        CREDIT_LIMIT_EXCEEDED
    }

    private final Reason reason;

    public VendingException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
