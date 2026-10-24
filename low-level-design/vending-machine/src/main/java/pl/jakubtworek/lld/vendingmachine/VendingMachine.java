package pl.jakubtworek.lld.vendingmachine;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import static pl.jakubtworek.lld.vendingmachine.VendingException.Reason.*;

/**
 * Granica spójności pojedynczego automatu i pojedynczego klienta. Wpłata
 * trafia do depozytu (escrow), a nie od razu do kasety właściciela. Dzięki
 * temu anulowanie zawsze zwraca dokładnie przyjęte monety, nawet po odmowie
 * zakupu z braku reszty. Dopiero sukces łączy depozyt z kasetą i odejmuje resztę.
 *
 * Każde polecenie buduje kompletny kandydat, a potem publikuje go jednym
 * przypisaniem. Nie ma rollbacku częściowo zmniejszonego magazynu. Kontrakt
 * jest JEDNOWĄTKOWY, bez I/O: to nie atomowa transakcja fizycznego urządzenia
 * i nie ochrona dwóch równoległych klientów. Wymagania sprzętowe zmieniłyby model.
 */
public final class VendingMachine {

    public static final int MAX_CREDIT_GROSZ = ChangePolicy.MAX_AMOUNT_GROSZ;
    private final ChangePolicy planner;
    private MachineSnapshot state;

    public VendingMachine(List<StockItem> items, CoinBundle initialCash) {
        this(items, initialCash, new ExactChangePlanner());
    }

    /**
     * Politykę wybieramy dla automatu, nie podmieniamy jej w środku sesji.
     */
    public VendingMachine(List<StockItem> items, CoinBundle initialCash, ChangePolicy planner) {
        this.planner = Objects.requireNonNull(planner, "planner");
        Objects.requireNonNull(initialCash, "initialCash");
        var stock = new HashMap<SlotId, StockItem>();
        for (StockItem item : List.copyOf(items)) {
            if (item.product().price().grosz() > MAX_CREDIT_GROSZ) {
                throw new IllegalArgumentException("Cena przekracza maksymalną obsługiwaną wpłatę");
            }
            if (stock.putIfAbsent(item.slotId(), item) != null) {
                throw new IllegalArgumentException("Kody slotów muszą być unikalne");
            }
        }
        if (stock.isEmpty()) {
            throw new IllegalArgumentException("Automat wymaga co najmniej jednego slotu");
        }
        state = new MachineSnapshot(stock, initialCash, new PurchaseSession.Idle(), new Money(0), Optional.empty());
    }

    public MachineSnapshot snapshot() {
        return state;
    }

    public MachineSnapshot select(SlotId slotId) {
        Objects.requireNonNull(slotId, "slotId");
        requireIdle();
        StockItem item = item(slotId);
        if (item.quantity() == 0) {
            throw new VendingException(OUT_OF_STOCK, "Produkt wyczerpany");
        }
        var next = new MachineSnapshot(state.stock(), state.cash(), new PurchaseSession.Selected(slotId,
                CoinBundle.empty()), state.revenue(), state.lastSale());
        state = next;
        return state;
    }

    public MachineSnapshot insert(Coin coin) {
        Objects.requireNonNull(coin, "coin");
        SlotId slot = requireSelected();
        CoinBundle escrow = state.escrow().plus(CoinBundle.of(coin));
        if (escrow.total().grosz() > MAX_CREDIT_GROSZ) {
            throw new VendingException(CREDIT_LIMIT_EXCEEDED, "Wpłata przekroczyłaby limit 100 PLN");
        }
        // Ta zmiana dotyczy tylko depozytu. Kaseta i przychód nie rosną od
        // samego wrzucenia monety; zakup może się jeszcze nie udać lub zostać anulowany.
        var next = new MachineSnapshot(state.stock(), state.cash(), new PurchaseSession.Selected(slot, escrow),
                state.revenue(), state.lastSale());
        state = next;
        return state;
    }

    public SaleReceipt buy() {
        SlotId slot = requireSelected();
        StockItem selected = item(slot);
        CoinBundle inserted = state.escrow();
        if (inserted.total().grosz() < selected.product().price().grosz()) {
            throw new VendingException(INSUFFICIENT_FUNDS, "Wpłata jest mniejsza niż cena");
        }
        Money changeAmount = inserted.total().minus(selected.product().price());
        CoinBundle available = state.cash().plus(inserted);
        CoinBundle change = planner.plan(changeAmount, available)
                .orElseThrow(() -> new VendingException(NO_EXACT_CHANGE, "Brak monet do wydania dokładnej reszty"));
        // Granica rozszerzenia też wymaga obrony: receipt sprawdzi wartość,
        // a minus konkretne nominały. Wadliwa polityka nie może opublikować
        // ujemnej kasety. Kompletność i minimum weryfikujemy testami strategii.
        CoinBundle remainingCash = available.minus(change);
        // Wszystkie zawodniejsze obliczenia i walidacje są przed publikacją.
        // Brak reszty nie zabiera produktu ani wpłaty. Część wpłaconych monet
        // może posłużyć do reszty, ale dopiero w zatwierdzonym zakupie.
        var receipt = new SaleReceipt(slot, selected.product(), inserted, change);
        // Kopiujemy wpisy mapy, nie całe produkty. Niemutowalność elementów
        // pozwala współdzielić je bez zmiany starszego snapshotu.
        var stock = new HashMap<>(state.stock());
        stock.put(slot, selected.dispense());
        var next = new MachineSnapshot(stock, remainingCash, new PurchaseSession.Idle(), state.revenue()
                .plus(selected.product().price()), Optional.of(receipt));
        state = next;
        return receipt;
    }

    public CoinBundle cancel() {
        requireSelected();
        CoinBundle refund = state.escrow();
        // Nie planujemy zwrotu z kasety: depozyt wciąż fizycznie reprezentujemy
        // osobno. Wartość może być równa innemu zestawowi, lecz kontrakt zwraca
        // oryginalne nominały. Działa to także bez żadnych monet w kasecie.
        var next = new MachineSnapshot(state.stock(), state.cash(), new PurchaseSession.Idle(), state.revenue(),
                state.lastSale());
        state = next;
        return refund;
    }

    public MachineSnapshot restock(SlotId slotId, int quantity) {
        Objects.requireNonNull(slotId, "slotId");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Uzupełnienie wymaga dodatniej liczby sztuk");
        }
        requireIdle();
        StockItem replacement = item(slotId).restock(quantity);
        var stock = new HashMap<>(state.stock());
        stock.put(slotId, replacement);
        var next = new MachineSnapshot(stock, state.cash(), new PurchaseSession.Idle(), state.revenue(),
                state.lastSale());
        state = next;
        return state;
    }

    public MachineSnapshot loadCash(CoinBundle coins) {
        Objects.requireNonNull(coins, "coins");
        requireIdle();
        // Uzupełnienie kasety nie jest przychodem ze sprzedaży. Zmieszanie
        // tych dwóch pojęć zepsułoby sprawdzanie zachowania wartości pieniądza.
        var next = new MachineSnapshot(state.stock(), state.cash().plus(coins), new PurchaseSession.Idle(),
                state.revenue(), state.lastSale());
        state = next;
        return state;
    }

    private StockItem item(SlotId slotId) {
        StockItem item = state.stock().get(slotId);
        if (item == null) {
            throw new VendingException(UNKNOWN_SLOT, "Nieznany kod produktu");
        }
        return item;
    }

    private void requireIdle() {
        if (state.phase() != MachinePhase.IDLE) {
            throw new VendingException(PURCHASE_IN_PROGRESS, "Najpierw zakończ lub anuluj bieżący zakup");
        }
    }

    private SlotId requireSelected() {
        return state.selectedSlot().orElseThrow(() -> new VendingException(NO_ACTIVE_PURCHASE,
                "Najpierw wybierz produkt"));
    }
}
