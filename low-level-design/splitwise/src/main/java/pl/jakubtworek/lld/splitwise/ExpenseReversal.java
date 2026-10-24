package pl.jakubtworek.lld.splitwise;

import java.util.Objects;

/**
 * Korekta wskazuje źródłowy wydatek zamiast kopiować jego udziały. Dziennik
 * pozostaje kompletny, a replay odwraca dokładnie pierwotny efekt. Nie cofamy
 * spłat ani faktycznych przelewów; po korekcie znaki sald mogą się odwrócić.
 */
public record ExpenseReversal(EntryId id, EntryId expenseId, MemberId actor, String reason) implements LedgerEntry {

    public ExpenseReversal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(expenseId, "expenseId");
        Objects.requireNonNull(actor, "actor");
        reason = Objects.requireNonNull(reason, "reason").strip();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("Korekta wymaga powodu");
        }
    }
}
