package pl.jakubtworek.lld.atm;

import java.util.Objects;

/**
 * Zakres retry to konto i klucz w jednej instancji bankomatu; kilka kart tego konta współdzieli rejestr.
 */
public record WithdrawalKey(AccountId account, OperationKey operation) {

    public WithdrawalKey {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(operation, "operation");
    }
}
