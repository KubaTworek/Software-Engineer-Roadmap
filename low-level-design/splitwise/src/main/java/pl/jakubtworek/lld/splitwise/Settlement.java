package pl.jakubtworek.lld.splitwise;

import java.util.Objects;

/**
 * Zapis zgłoszonej spłaty, nie integracja bankowa. Dłużnik otrzymuje dodatnią
 * zmianę salda, wierzyciel ujemną. Uprawnienie i granice kwoty zależą od grupy,
 * więc nie mogą zostać sprawdzone przez sam ten niemutowalny rekord.
 */
public record Settlement(EntryId id, MemberId from, MemberId to, Money amount) implements LedgerEntry {

    public Settlement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        if (amount.grosz() == 0 || from.equals(to)) {
            throw new IllegalArgumentException("Spłata wymaga dodatniej kwoty i dwóch różnych osób");
        }
    }
}
