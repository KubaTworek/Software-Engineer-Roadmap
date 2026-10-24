package pl.jakubtworek.lld.atm;

import java.util.Objects;

/**
 * Konto chroni brak debetu. debit tworzy kandydata, nie zapisuje go w banku ani
 * bankomacie. Limit i nominały należą do wypłaty bankomatu, nie do definicji konta.
 */
public record Account(AccountId id, Money balance) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(balance, "balance");
    }

    public Account debit(Money amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.grosz() == 0) {
            throw new AtmException(AtmException.Reason.INVALID_AMOUNT, "Obciążenie musi być dodatnie");
        }
        if (balance.grosz() < amount.grosz()) {
            throw new AtmException(AtmException.Reason.INSUFFICIENT_FUNDS, "Brak środków na koncie");
        }
        return new Account(id, balance.minus(amount));
    }
}
