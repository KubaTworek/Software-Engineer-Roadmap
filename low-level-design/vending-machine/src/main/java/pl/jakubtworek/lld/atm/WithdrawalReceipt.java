package pl.jakubtworek.lld.atm;

import java.util.Objects;
import java.util.UUID;

/**
 * Potwierdzenie jednej przyjętej wypłaty, z kwotą, konkretnymi banknotami i saldem po operacji; bez PIN-u.
 */
public record WithdrawalReceipt(UUID id, AccountId account, Money amount, CashBundle cash, Money balanceAfter) {

    public WithdrawalReceipt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(cash, "cash");
        Objects.requireNonNull(balanceAfter, "balanceAfter");
        if (amount.grosz() == 0 || !amount.equals(cash.total())) {
            throw new IllegalArgumentException("Kwota potwierdzenia musi odpowiadać banknotom");
        }
    }
}
