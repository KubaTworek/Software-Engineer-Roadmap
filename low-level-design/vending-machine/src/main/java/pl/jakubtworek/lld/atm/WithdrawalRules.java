package pl.jakubtworek.lld.atm;

import java.util.Objects;
import static pl.jakubtworek.lld.atm.AtmException.Reason.*;

/**
 * Jedna definicja dopuszczalnej kwoty dla koordynatora i planera. Limit 2000 PLN
 * jest regułą tego ćwiczenia, a zarazem ogranicza tablicę DP do 200 jednostek
 * po 10 PLN. Nie jest dziennym limitem ani opisem oferty rzeczywistego banku.
 */
public final class WithdrawalRules {

    public static final Money MAX_AMOUNT = new Money(200000);

    private WithdrawalRules() {
    }

    public static void requireAllowed(Money amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.grosz() == 0 || amount.grosz() % 1000 != 0) {
            throw new AtmException(INVALID_AMOUNT, "Wypłata wymaga dodatniej wielokrotności 10 PLN");
        }
        if (amount.grosz() > MAX_AMOUNT.grosz()) {
            throw new AtmException(AMOUNT_LIMIT_EXCEEDED, "Limit jednej wypłaty to 2000 PLN");
        }
    }
}
