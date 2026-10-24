package pl.jakubtworek.lld.vendingmachine;

import java.util.List;

/**
 * Mały katalog testu ułatwia odróżnienie zmiany magazynu od zmiany sesji.
 */
final class Fixtures {

    static final SlotId SLOT = new SlotId("A1");

    static VendingMachine machine(long price, int quantity, CoinBundle cash) {
        return new VendingMachine(List.of(new StockItem(SLOT, new Product("Produkt", new Money(price)), quantity)),
                cash);
    }
}
