package pl.jakubtworek.lld.atm;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Jeden opublikowany stan lokalnego modelu. Mapy i listy są kopiowane, a ich
 * wartości niemutowalne. To widok diagnostyczny całego ćwiczenia, nie publiczne
 * API bankowe ujawniające klientowi cudze konta. Nie przechowuje PIN-ów.
 */
public final class AtmSnapshot {

    private final Map<AccountId, Account> accounts;
    private final CashInventory cash;
    private final Session session;
    private final List<WithdrawalReceipt> withdrawals;

    // Klucze są częścią publikacji pieniędzy, nie osobną pamięcią podręczną.
    // Ich oddzielny zapis mógłby zwrócić wynik nieistniejący w historii.
    private final Map<WithdrawalKey, WithdrawalReceipt> operations;

    AtmSnapshot(Map<AccountId, Account> accounts, CashInventory cash, Session session,
            List<WithdrawalReceipt> withdrawals, Map<WithdrawalKey, WithdrawalReceipt> operations) {
        this.accounts = Map.copyOf(accounts);
        this.cash = Objects.requireNonNull(cash, "cash");
        this.session = Objects.requireNonNull(session, "session");
        this.withdrawals = List.copyOf(withdrawals);
        this.operations = Map.copyOf(operations);
    }

    public Map<AccountId, Account> accounts() {
        return accounts;
    }

    public CashInventory cash() {
        return cash;
    }

    public Session session() {
        return session;
    }

    public List<WithdrawalReceipt> withdrawals() {
        return withdrawals;
    }

    public Map<WithdrawalKey, WithdrawalReceipt> operations() {
        return operations;
    }
}
