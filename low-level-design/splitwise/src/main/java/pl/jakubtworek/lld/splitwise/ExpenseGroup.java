package pl.jakubtworek.lld.splitwise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import static pl.jakubtworek.lld.splitwise.SplitException.Reason.*;

/**
 * Granica spójności dziennika i sald grupy. Polityka liczy udziały, ale nie
 * zna składu grupy ani nie publikuje wpisów. Tutaj sprawdzamy uczestników,
 * kierunek spłaty i kandydackie delty. Tablica long[] przechowuje projekcję sald;
 * indeksy hash eliminują wyszukiwanie wpisu i korekty w całej historii.
 * Model JEDNOWĄTKOWY, bez I/O: publikacja nie chroni przed równoległymi zapisami.
 */
public final class ExpenseGroup {

    private final Map<MemberId, Member> members;
    private final ArrayList<LedgerEntry> entries = new ArrayList<>();
    private final Map<EntryId, LedgerEntry> byId = new HashMap<>();
    private final java.util.Set<EntryId> reversedExpenses = new java.util.HashSet<>();
    private final Map<OperationKey, RecordedOperation> operations = new HashMap<>();
    private final Map<MemberId, Integer> positions = new HashMap<>();

    // Prymitywne salda nie wymagają Long na każdą aktualizację. final chroni
    // referencję tablicy, nie jej komórki; zapis przechodzi wyłącznie przez append.
    private final long[] balances;
    private GroupSnapshot cached;

    public ExpenseGroup(List<Member> members) {
        var known = new HashMap<MemberId, Member>();
        for (Member member : List.copyOf(members)) {
            if (known.putIfAbsent(member.id(), member) != null) {
                throw new IllegalArgumentException("Powtórzony kod uczestnika");
            }
        }
        if (known.isEmpty()) {
            throw new IllegalArgumentException("Grupa wymaga uczestników");
        }
        this.members = Map.copyOf(known);
        int position = 0;
        for (MemberId id : known.keySet()) {
            positions.put(id, position++);
        }
        balances = new long[known.size()];
    }

    public GroupSnapshot snapshot() {
        if (cached == null) {
            cached = new GroupSnapshot(members, entries, operations);
        }
        return cached;
    }

    /**
     * Wariant wygodny dla pojedynczego polecenia: każde wywołanie jest nową intencją, także z identycznymi danymi.
     */
    public Expense addExpense(MemberId payer, Money total, SplitPolicy policy, String description) {
        return addExpense(OperationKey.fresh(), payer, total, policy, description);
    }

    /**
     * Retry porównuje końcowe udziały; polityka nadal musi być czysta i deterministyczna.
     */
    public Expense addExpense(OperationKey key, MemberId payer, Money total, SplitPolicy policy, String description) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(payer, "payer");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(description, "description");
        var request = new OperationRequest.ExpenseRequest(payer, total, policy.split(total), description);
        var previous = replay(key, request);
        if (previous != null) {
            return (Expense) previous;
        }
        known(payer);
        positive(total);
        Map<MemberId, Money> shares = request.shares();
        shares.keySet().forEach(this::known);
        // Expense sprawdza sumę i kopiuje mapę. Wadliwa dodatkowa strategia nie
        // może opublikować niepełnego podziału ani później zmienić jego wnętrza.
        var expense = new Expense(new EntryId(UUID.randomUUID()), payer, total, shares, request.description());
        append(key, request, expense);
        return expense;
    }

    /**
     * Bez klucza nie rozpoznajemy retry; wywołujący potrzebujący deduplikacji wybiera przeciążenie z OperationKey.
     */
    public Settlement settle(MemberId from, MemberId to, Money amount) {
        return settle(OperationKey.fresh(), from, to, amount);
    }

    public Settlement settle(OperationKey key, MemberId from, MemberId to, Money amount) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        var request = new OperationRequest.SettlementRequest(from, to, amount);
        var previous = replay(key, request);
        // Powtórka udanej spłaty musi wrócić PRZED kontrolą dzisiejszych sald:
        // pierwotna spłata mogła już wyzerować dług i należność.
        if (previous != null) {
            return (Settlement) previous;
        }
        known(from);
        known(to);
        positive(amount);
        if (from.equals(to)) {
            throw new SplitException(SELF_SETTLEMENT, "Nie można spłacić samego siebie");
        }
        long debtor = balance(from);
        long creditor = balance(to);
        if (debtor >= 0 || creditor <= 0) {
            throw new SplitException(INVALID_SETTLEMENT_DIRECTION, "Spłata wymaga dłużnika i wierzyciela");
        }
        // Nie negujemy całego długu: dla Long.MIN_VALUE byłoby to niemożliwe.
        // Zmiany w kierunku zera są bezpieczne nawet na skraju zakresu long.
        if (Math.addExact(debtor, amount.grosz()) > 0 || Math.subtractExact(creditor, amount.grosz()) < 0) {
            throw new SplitException(OVERPAYMENT, "Kwota przekracza dług lub należność");
        }
        var settlement = new Settlement(new EntryId(UUID.randomUUID()), from, to, amount);
        append(key, request, settlement);
        return settlement;
    }

    /**
     * Korekta odwraca koszt, nie przelewy. Po wcześniejszej spłacie pierwotny
     * płatnik może stać się dłużnikiem i zwrócić otrzymaną kwotę kolejną spłatą.
     * Aktor jest deklarowaną rolą domenową, nie uwierzytelnionym użytkownikiem.
     * Nowy klucz nie pozwala drugi raz odwrócić tego samego wydatku, natomiast
     * retry z dawnym kluczem zwraca dawny wynik przed kontrolą ALREADY_REVERSED.
     */
    public ExpenseReversal reverseExpense(OperationKey key, MemberId actor, EntryId expenseId, String reason) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(expenseId, "expenseId");
        var request = new OperationRequest.ReversalRequest(actor, expenseId, reason);
        var previous = replay(key, request);
        if (previous != null) {
            return (ExpenseReversal) previous;
        }
        known(actor);
        var source = byId.get(expenseId);
        if (source == null) {
            throw new SplitException(UNKNOWN_ENTRY, "Nieznany wpis dziennika");
        }
        if (!(source instanceof Expense expense)) {
            throw new SplitException(NOT_EXPENSE, "Odwrócić można tylko wydatek");
        }
        if (!expense.payer().equals(actor)) {
            throw new SplitException(NOT_PAYER, "Korektę zgłasza płatnik wydatku");
        }
        boolean reversed = reversedExpenses.contains(expenseId);
        if (reversed) {
            throw new SplitException(ALREADY_REVERSED, "Wydatek był już odwrócony");
        }
        var result = new ExpenseReversal(new EntryId(UUID.randomUUID()), expenseId, actor, request.reason());
        append(key, request, result);
        return result;
    }

    // Rodzaj żądania jest częścią jego równości. Dzięki temu przed rzutowaniem
    // wyniku wiemy, że klucz wydatku nie zostanie użyty jako klucz spłaty.
    private LedgerEntry replay(OperationKey key, OperationRequest request) {
        var previous = operations.get(key);
        if (previous == null) {
            return null;
        }
        if (!previous.request().equals(request)) {
            throw new SplitException(IDEMPOTENCY_CONFLICT, "Klucz został użyty z innymi danymi");
        }
        return previous.result();
    }

    private void append(OperationKey key, OperationRequest request, LedgerEntry entry) {
        // Bufor zmian dotyczy tylko uczestników wpisu. Wszystkie addExact wykonujemy
        // przed mutacją tablicy, dziennika i indeksów: odmowa nie zużywa klucza retry.
        var changes = new HashMap<Integer, Long>();
        switch(entry) {
            case Expense expense ->
                expenseChanges(changes, expense, false);
            case ExpenseReversal reversal ->
                expenseChanges(changes, (Expense) byId.get(reversal.expenseId()), true);
            case Settlement settlement ->
                {
                    candidate(changes, settlement.from(), settlement.amount().grosz());
                    candidate(changes, settlement.to(), -settlement.amount().grosz());
                }
        }
        // Najpierw policzyliśmy wszystkie delty z kontrolą przepełnienia.
        // Ten zapis nie jest atomowy między wątkami; bufor chroni przed
        // częściowym efektem błędu arytmetyki w modelu jednowątkowym.
        changes.forEach((index, value) -> balances[index] = value);
        entries.add(entry);
        byId.put(entry.id(), entry);
        if (entry instanceof ExpenseReversal reversal) {
            reversedExpenses.add(reversal.expenseId());
        }
        operations.put(key, new RecordedOperation(request, entry));
        cached = null;
    }

    private void expenseChanges(Map<Integer, Long> changes, Expense expense, boolean reverse) {
        long own = expense.shares().getOrDefault(expense.payer(), new Money(0)).grosz();
        long delta = expense.total().grosz() - own;
        candidate(changes, expense.payer(), reverse ? -delta : delta);
        expense.shares().forEach((member, share) -> {
            if (!member.equals(expense.payer())) {
                candidate(changes, member, reverse ? share.grosz() : -share.grosz());
            }
        });
    }

    private void candidate(Map<Integer, Long> changes, MemberId member, long delta) {
        int index = positions.get(member);
        changes.put(index, Math.addExact(changes.getOrDefault(index, balances[index]), delta));
    }

    private long balance(MemberId member) {
        known(member);
        return balances[positions.get(member)];
    }

    private void known(MemberId member) {
        if (!positions.containsKey(member)) {
            throw new SplitException(UNKNOWN_MEMBER, "Nieznany uczestnik grupy");
        }
    }

    private static void positive(Money amount) {
        if (amount.grosz() == 0) {
            throw new SplitException(INVALID_AMOUNT, "Kwota musi być dodatnia");
        }
    }
}
