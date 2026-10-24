package pl.jakubtworek.lld.splitwise;

import java.util.Map;
import java.util.Objects;

/**
 * Zapamiętujemy wynik podziału, nie obiekt polityki. Zmiana algorytmu w przyszłości
 * nie może zmienić udziałów dawnego obiadu. Mapa jest kopiowana; Money i ID
 * są niemutowalne. Publiczna wartość nie uprawnia do wstawienia jej do dziennika.
 */
public record Expense(EntryId id, MemberId payer, Money total, Map<MemberId, Money> shares,
        String description) implements LedgerEntry {

    public Expense {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(payer, "payer");
        Objects.requireNonNull(total, "total");
        shares = Map.copyOf(shares);
        description = Objects.requireNonNull(description, "description").strip();
        if (total.grosz() == 0 || shares.isEmpty() || description.isEmpty()) {
            throw new IllegalArgumentException("Wydatek wymaga kwoty, udziałów i opisu");
        }
        Money sum = new Money(0);
        for (Money share : shares.values()) {
            sum = sum.plus(share);
        }
        if (!sum.equals(total)) {
            throw new IllegalArgumentException("Udziały muszą sumować się do kwoty wydatku");
        }
    }
}
