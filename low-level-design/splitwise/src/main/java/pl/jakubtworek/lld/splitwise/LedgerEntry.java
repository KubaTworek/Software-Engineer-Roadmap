package pl.jakubtworek.lld.splitwise;

/**
 * Dziennik rozróżnia koszt, spłatę i korektę kosztu. Sealed interface daje jawne warianty
 * odtwarzania sald bez flagi typu i wielu opcjonalnych pól w jednym rekordzie.
 */
public sealed interface LedgerEntry permits Expense, Settlement, ExpenseReversal {

    EntryId id();
}
