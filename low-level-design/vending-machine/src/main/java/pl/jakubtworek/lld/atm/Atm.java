package pl.jakubtworek.lld.atm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.time.Clock;
import java.time.Duration;
import static pl.jakubtworek.lld.atm.AtmException.Reason.*;

/**
 * Granica spójności lokalnej wypłaty: sesja, konta, kaseta i potwierdzenia
 * są aktualizowane po przygotowaniu kompletu wartości. Indeksy hash i listy
 * eliminują kopiowanie historii przy wypłacie; snapshot materializujemy leniwie.
 * Model jednowątkowy,
 * bez rzeczywistych przelewów i mechanizmu wydającego gotówkę. Taki zapis NIE
 * rozwiązuje awarii między obciążeniem zdalnego banku a fizyczną wypłatą.
 */
public final class Atm {

    private final Map<CardId, AccountId> cards;
    private final CardAuthenticator authenticator;
    private final BoundedCashPlanner planner = new BoundedCashPlanner();
    private final Clock clock;
    private final Duration sessionTtl;
    private final Map<AccountId, Account> accounts;
    private final ArrayList<WithdrawalReceipt> withdrawals = new ArrayList<>();
    private final Map<WithdrawalKey, WithdrawalReceipt> operations = new HashMap<>();

    // final chroni referencję mapy, nie listy wewnątrz. Snapshot nie może
    // wystawić tych samych mutowalnych list jako stabilnej historii.
    private final Map<AccountId, ArrayList<WithdrawalReceipt>> accountHistory = new HashMap<>();
    private CashInventory cash;
    private Session session = new Session.Idle();
    private AtmSnapshot cached;

    public Atm(List<Account> accounts, Map<CardId, AccountId> cards, CashInventory cash,
            CardAuthenticator authenticator) {
        this(accounts, cards, cash, authenticator, Clock.systemUTC(), Duration.ofMinutes(5));
    }

    public Atm(List<Account> accounts, Map<CardId, AccountId> cards, CashInventory cash,
            CardAuthenticator authenticator, Clock clock, Duration sessionTtl) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sessionTtl = Objects.requireNonNull(sessionTtl, "sessionTtl");
        if (sessionTtl.isNegative() || sessionTtl.isZero()) {
            throw new IllegalArgumentException("Czas ważności sesji musi być dodatni");
        }
        this.cards = Map.copyOf(cards);
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
        var known = new HashMap<AccountId, Account>();
        for (var account : List.copyOf(accounts)) {
            if (known.putIfAbsent(account.id(), account) != null) {
                throw new IllegalArgumentException("Powtórzone konto");
            }
        }
        if (known.isEmpty() || this.cards.isEmpty() || !known.keySet().containsAll(this.cards.values())) {
            throw new IllegalArgumentException("Karty muszą wskazywać istniejące konta w niepustej konfiguracji");
        }
        this.accounts = known;
        this.cash = Objects.requireNonNull(cash, "cash");
    }

    public AtmSnapshot snapshot() {
        if (cached == null) {
            cached = new AtmSnapshot(accounts, cash, session, withdrawals, operations);
        }
        return cached;
    }

    public SessionId insertCard(CardId card) {
        Objects.requireNonNull(card, "card");
        var now = clock.instant();
        if (session instanceof Session.Active active && now.isBefore(active.expiresAt())) {
            throw invalidSession();
        }
        if (!cards.containsKey(card)) {
            throw new AtmException(UNKNOWN_CARD, "Nieznana karta ćwiczenia");
        }
        // Obliczenie terminu może się przepełnić: wtedy nie publikujemy ani
        // nowej sesji, ani częściowego zamknięcia poprzedniej. Znana karta może
        // zastąpić już wygasłą sesję jednym kompletnym zapisem.
        var deadline = now.plus(sessionTtl);
        var id = new SessionId(UUID.randomUUID());
        publishSession(new Session.CardInserted(id, card, 0, deadline));
        return id;
    }

    public AuthenticationResult enterPin(SessionId id, Pin pin) {
        Objects.requireNonNull(pin, "pin");
        if (!(active(id) instanceof Session.CardInserted inserted)) {
            throw invalidSession();
        }
        // Najpierw wynik zależności, dopiero publikacja. Awaria adaptera nie
        // jest błędnym PIN-em i nie zwiększa licznika. PIN nie trafia do stanu.
        if (authenticator.authenticate(inserted.card(), pin)) {
            publishSession(new Session.Authenticated(inserted.id(), inserted.card(), cards.get(inserted.card()),
                    inserted.expiresAt()));
            return AuthenticationResult.AUTHENTICATED;
        }
        if (inserted.failedAttempts() == 2) {
            publishSession(new Session.Idle());
            return AuthenticationResult.CARD_EJECTED;
        }
        publishSession(new Session.CardInserted(inserted.id(), inserted.card(), inserted.failedAttempts() + 1,
                inserted.expiresAt()));
        return AuthenticationResult.REJECTED;
    }

    public Money balance(SessionId id) {
        return accounts.get(authenticated(id).account()).balance();
    }

    public List<WithdrawalReceipt> history(SessionId id) {
        var account = authenticated(id).account();
        return List.copyOf(accountHistory.getOrDefault(account, new ArrayList<>()));
    }

    /**
     * Bez jawnego klucza każde wywołanie jest nową intencją, ale nadal wymaga konkretnego ID sesji.
     */
    public WithdrawalReceipt withdraw(SessionId id, Money amount) {
        return withdraw(id, OperationKey.fresh(), amount);
    }

    public WithdrawalReceipt withdraw(SessionId id, OperationKey key, Money amount) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(key, "key");
        // Prawo dostępu sprawdzamy przed retry. Znajomość dawnego klucza nie
        // pozwala odebrać potwierdzenia przez obcą, starą lub wygasłą sesję.
        var authorized = authenticated(id);
        var scopedKey = new WithdrawalKey(authorized.account(), key);
        var previous = operations.get(scopedKey);
        if (previous != null) {
            if (!previous.amount().equals(amount)) {
                throw new AtmException(IDEMPOTENCY_CONFLICT, "Klucz wypłaty został użyty z inną kwotą");
            }
            return previous;
        }
        WithdrawalRules.requireAllowed(amount);
        // debit jest czystą funkcją zwracającą nowe konto. Przy późniejszym
        // braku gotówki kandydat nie zostanie opublikowany, więc saldo nie spada.
        var nextAccount = accounts.get(authorized.account()).debit(amount);
        if (cash.total().grosz() < amount.grosz()) {
            throw new AtmException(INSUFFICIENT_CASH, "Kaseta ma zbyt małą łączną wartość");
        }
        var bundle = planner.plan(amount, cash).orElseThrow(() -> new AtmException(CANNOT_DISPENSE,
                "Brak dokładnej kombinacji nominałów"));
        if (!bundle.total().equals(amount)) {
            throw new IllegalStateException("Planer zwrócił inną kwotę");
        }
        var nextCash = cash.without(bundle);
        var receipt = new WithdrawalReceipt(UUID.randomUUID(), authorized.account(), amount, bundle,
                nextAccount.balance());
        // Plan, nowe saldo i kaseta są już poprawnymi wartościami. Zatwierdzenie
        // dotyka jednego konta i dopisuje po jednym rekordzie do indeksów hash/list.
        // Nie kopiujemy historii H przy każdej wypłacie; pełny widok powstaje na żądanie.
        accounts.put(nextAccount.id(), nextAccount);
        cash = nextCash;
        withdrawals.add(receipt);
        // Listę tworzymy raz na konto. computeIfAbsent zwykłej mapy i add
        // listy nie synchronizują wspólnej aktualizacji konta oraz kasety.
        accountHistory.computeIfAbsent(receipt.account(), ignored -> new ArrayList<>()).add(receipt);
        operations.put(scopedKey, receipt);
        cached = null;
        return receipt;
    }

    public CardId ejectCard(SessionId id) {
        CardId card = active(id).card();
        publishSession(new Session.Idle());
        return card;
    }

    private Session.Authenticated authenticated(SessionId id) {
        if (active(id) instanceof Session.Authenticated session) {
            return session;
        }
        throw invalidSession();
    }

    private Session.Active active(SessionId id) {
        Objects.requireNonNull(id, "id");
        if (!(session instanceof Session.Active session)) {
            throw invalidSession();
        }
        // Stare polecenie nie czyta nawet zegara. Nie może przez sprawdzanie
        // timeoutu wygasić nowej sesji, również gdy ta faktycznie jest po terminie.
        if (!session.id().equals(id)) {
            throw new AtmException(STALE_SESSION, "Polecenie pochodzi z innej sesji");
        }
        if (!clock.instant().isBefore(session.expiresAt())) {
            publishSession(new Session.Idle());
            throw new AtmException(SESSION_EXPIRED, "Sesja wygasła i karta została zwrócona");
        }
        return session;
    }

    private static AtmException invalidSession() {
        return new AtmException(INVALID_SESSION_STATE, "Operacja niedozwolona w bieżącym stanie sesji");
    }

    private void publishSession(Session next) {
        session = next;
        cached = null;
    }
}
