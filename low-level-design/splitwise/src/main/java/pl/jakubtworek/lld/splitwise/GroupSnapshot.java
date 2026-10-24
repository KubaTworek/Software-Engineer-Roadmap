package pl.jakubtworek.lld.splitwise;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Źródłem prawdy są wpisy. Mapa sald jest w całości odtwarzana przy budowie
 * widoku, nie aktualizowana niezależnym setterem. Odczyt salda jest potem O(1).
 * Odtworzenie wykonujemy przy materializacji widoku, nie przy każdej komendzie.
 * ExpenseGroup aktualizuje salda przyrostowo; ta ścieżka pozostaje niezależną
 * projekcją odczytową oraz kontrolą spójności dziennika.
 */
public final class GroupSnapshot {

    private final Map<MemberId, Member> members;
    private final List<LedgerEntry> entries;
    private final Map<MemberId, Balance> balances;

    // Metadanych retry nie wyprowadzimy z samych kwot dziennika. Publikujemy
    // je razem ze stanem; trwały wariant musiałby zachować także te żądania.
    private final Map<OperationKey, RecordedOperation> recordedOperations;
    private final Map<OperationKey, EntryId> operationEntries;

    GroupSnapshot(Map<MemberId, Member> members, List<LedgerEntry> entries, Map<OperationKey,
            RecordedOperation> operations) {
        this.members = Map.copyOf(members);
        this.entries = List.copyOf(entries);
        recordedOperations = Map.copyOf(operations);
        var results = new HashMap<OperationKey, EntryId>();
        operations.forEach((key, operation) -> results.put(key, operation.result().id()));
        operationEntries = Map.copyOf(results);
        var values = new HashMap<MemberId, Long>();
        var expenses = new HashMap<EntryId, Expense>();
        var reversed = new HashSet<EntryId>();
        members.keySet().forEach(member -> values.put(member, 0L));
        for (LedgerEntry entry : this.entries) {
            switch(entry) {
                case Expense expense ->
                    {
                        expenses.put(expense.id(), expense);
                        applyExpense(values, expense, false);
                    }
                case Settlement settlement ->
                    {
                        add(values, settlement.from(), settlement.amount().grosz());
                        add(values, settlement.to(), -settlement.amount().grosz());
                    }
                case ExpenseReversal reversal ->
                    {
                        Expense source = expenses.get(reversal.expenseId());
                        if (source == null || !source.payer().equals(reversal.actor()) || !reversed.add(source.id())) {
                            throw new IllegalStateException("Niespójna korekta w dzienniku");
                        }
                        // Odwracamy pierwotny wydatek W TYM miejscu historii.
                        // Wcześniejsze spłaty pozostają ważnymi zapisami transferów.
                        applyExpense(values, source, true);
                    }
            }
        }
        // Indywidualne salda mieszczą się w long, lecz pośrednia suma dodatnich
        // sald może go przekroczyć. BigInteger nie tworzy fałszywego błędu kontroli.
        var sum = values.values().stream().map(BigInteger::valueOf).reduce(BigInteger.ZERO, BigInteger::add);
        if (sum.signum() != 0) {
            throw new IllegalStateException("Suma sald nie jest zerowa");
        }
        var projection = new HashMap<MemberId, Balance>();
        values.forEach((id, value) -> projection.put(id, new Balance(value)));
        balances = Map.copyOf(projection);
    }

    private static void add(Map<MemberId, Long> values, MemberId member, long delta) {
        values.put(member, Math.addExact(values.get(member), delta));
    }

    private static void applyExpense(Map<MemberId, Long> values, Expense expense, boolean reverse) {
        long ownShare = expense.shares().getOrDefault(expense.payer(), new Money(0)).grosz();
        // Aktualizacja netto zapobiega fałszywemu przepełnieniu płatnika.
        // Delta wydatku nie przekracza MAX_VALUE, więc jej negacja jest bezpieczna
        // także przy korekcie. Bieżące SALDO nadal może się przepełnić — wtedy
        // kandydat jest odrzucany przed publikacją dziennika i klucza operacji.
        long payerDelta = expense.total().grosz() - ownShare;
        add(values, expense.payer(), reverse ? -payerDelta : payerDelta);
        expense.shares().forEach((member, share) -> {
            if (!member.equals(expense.payer())) {
                add(values, member, reverse ? share.grosz() : -share.grosz());
            }
        });
    }

    public Map<MemberId, Member> members() {
        return members;
    }

    public List<LedgerEntry> entries() {
        return entries;
    }

    public Map<MemberId, Balance> balances() {
        return balances;
    }

    /**
     * Publiczny, niemutowalny widok wyników; szczegóły żądań pozostają w pakiecie modelu.
     */
    public Map<OperationKey, EntryId> operationEntries() {
        return operationEntries;
    }

    Map<OperationKey, RecordedOperation> recordedOperations() {
        return recordedOperations;
    }

    public Balance balanceOf(MemberId member) {
        Objects.requireNonNull(member, "member");
        Balance result = balances.get(member);
        if (result == null) {
            throw new SplitException(SplitException.Reason.UNKNOWN_MEMBER, "Nieznany uczestnik grupy");
        }
        return result;
    }
}
