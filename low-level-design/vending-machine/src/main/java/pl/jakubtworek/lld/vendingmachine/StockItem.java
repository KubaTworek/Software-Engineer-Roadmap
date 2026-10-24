package pl.jakubtworek.lld.vendingmachine;

import java.util.Objects;

/**
 * Stan magazynowy jednego slotu. Nieujemność jest lokalnym niezmiennikiem;
 * metody tworzą nową wartość zamiast udostępniać setter quantity.
 * Zmniejszenie ilości publikuje dopiero VendingMachine razem z rozliczeniem.
 */
public record StockItem(SlotId slotId, Product product, int quantity) {

    public StockItem {
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(product, "product");
        if (quantity < 0) {
            throw new IllegalArgumentException("Stan magazynowy nie może być ujemny");
        }
    }

    StockItem dispense() {
        if (quantity == 0) {
            throw new VendingException(VendingException.Reason.OUT_OF_STOCK, "Produkt wyczerpany");
        }
        return new StockItem(slotId, product, quantity - 1);
    }

    StockItem restock(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Uzupełnienie wymaga dodatniej liczby sztuk");
        }
        return new StockItem(slotId, product, Math.addExact(quantity, amount));
    }
}
