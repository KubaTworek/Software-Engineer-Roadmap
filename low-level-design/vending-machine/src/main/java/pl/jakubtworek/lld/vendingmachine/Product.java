package pl.jakubtworek.lld.vendingmachine;

import java.util.Objects;

/**
 * Niezmienny opis produktu i dodatniej ceny. Nie przechowuje liczby sztuk:
 * ten sam opis można umieścić w kilku slotach, każdy z własnym zapasem.
 * Maksymalną obsługiwaną cenę określa automat przy konfiguracji, bo jest
 * związana z limitem przyjmowanej wpłaty, nie samą wartością produktu.
 */
public record Product(String name, Money price) {

    public Product {
        name = Objects.requireNonNull(name, "name").strip();
        Objects.requireNonNull(price, "price");
        if (name.isEmpty() || price.grosz() == 0) {
            throw new IllegalArgumentException("Produkt wymaga nazwy i dodatniej ceny");
        }
    }
}
