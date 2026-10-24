package pl.jakubtworek.lld.vendingmachine;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Stabilny widok magazynu, kasety, sesji i przychodu. Wszystkie elementy są
 * niemutowalne, a mapa jest kopiowana. Konstruktor pozostaje w pakiecie domeny.
 * Wyprowadzenie fazy z sesji i ceny zapobiega sprzeczności „FUNDED bez środków”.
 * lastSale jest tylko ostatnim wynikiem, nie pełną historią wszystkich zakupów.
 */
public final class MachineSnapshot {

    private final Map<SlotId, StockItem> stock;
    private final CoinBundle cash;
    private final PurchaseSession session;
    private final Money revenue;
    private final Optional<SaleReceipt> lastSale;

    MachineSnapshot(Map<SlotId, StockItem> stock, CoinBundle cash, PurchaseSession session, Money revenue,
            Optional<SaleReceipt> lastSale) {
        this.stock = Map.copyOf(stock);
        this.cash = Objects.requireNonNull(cash, "cash");
        this.session = Objects.requireNonNull(session, "session");
        this.revenue = Objects.requireNonNull(revenue, "revenue");
        this.lastSale = Objects.requireNonNull(lastSale, "lastSale");
        if (session instanceof PurchaseSession.Selected selected && (!stock.containsKey(selected.slotId())
                || stock.get(selected.slotId()).quantity() == 0)) {
            throw new IllegalArgumentException("Sesja musi wskazywać dostępny slot");
        }
    }

    public Map<SlotId, StockItem> stock() {
        return stock;
    }

    public CoinBundle cash() {
        return cash;
    }

    public Money revenue() {
        return revenue;
    }

    public Optional<SaleReceipt> lastSale() {
        return lastSale;
    }

    public Optional<SlotId> selectedSlot() {
        return session instanceof PurchaseSession.Selected selected ? Optional.of(selected.slotId()) : Optional.empty();
    }

    public CoinBundle escrow() {
        return session instanceof PurchaseSession.Selected selected ? selected.inserted() : CoinBundle.empty();
    }

    public Money credit() {
        return escrow().total();
    }

    public MachinePhase phase() {
        if (session instanceof PurchaseSession.Idle) {
            return MachinePhase.IDLE;
        }
        var selected = (PurchaseSession.Selected) session;
        return selected.inserted().total().grosz() >= stock.get(selected.slotId()).product().price()
                .grosz() ? MachinePhase.FUNDED : MachinePhase.COLLECTING;
    }
}
