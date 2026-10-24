package pl.jakubtworek.lld.promotion.domain;

import java.util.Objects;

/**
 * Jedna pozycja koszyka, bez setterów i tożsamości magazynowej. Cena oznacza
 * cenę jednej sztuki przed promocjami. Kategoria może służyć warunkowi obecności
 * albo zakresowi rabatu; te dwie reguły są osobnymi obiektami. SKU jest kluczem
 * alokacji na całą linię (nie na pojedynczą sztukę).
 */
public record CartLine(String sku, String category, Money unitPrice, int quantity) {

    public CartLine {
        if (sku == null || !sku.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Niepoprawne SKU");
        if (category == null || !category.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Niepoprawna kategoria");
        Objects.requireNonNull(unitPrice);
        if (quantity <= 0)
            throw new IllegalArgumentException("Ilość musi być dodatnia");
        // Walidacja sumy pozycji przed przyjęciem jej do koszyka.
        unitPrice.times(quantity);
    }

    public Money total() {
        return unitPrice.times(quantity);
    }
}
