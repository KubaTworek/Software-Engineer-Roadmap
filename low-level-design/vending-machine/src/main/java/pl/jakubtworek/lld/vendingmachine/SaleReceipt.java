package pl.jakubtworek.lld.vendingmachine;

import java.util.Objects;

/**
 * Niezmienny wynik logicznego wydania produktu. Chroni równanie wpłata minus
 * reszta = cena. Publiczny klient może opisać takie dane, ale automat nie
 * przyjmuje zewnętrznego rachunku jako polecenia i nie uznaje go za dowód
 * wydania przez fizyczny silnik. Nie jest to dokument fiskalny.
 */
public record SaleReceipt(SlotId slotId, Product product, CoinBundle inserted, CoinBundle change) {

    public SaleReceipt {
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(inserted, "inserted");
        Objects.requireNonNull(change, "change");
        if (!inserted.total().minus(change.total()).equals(product.price())) {
            throw new IllegalArgumentException("Wpłata minus reszta musi być równa cenie");
        }
    }
}
