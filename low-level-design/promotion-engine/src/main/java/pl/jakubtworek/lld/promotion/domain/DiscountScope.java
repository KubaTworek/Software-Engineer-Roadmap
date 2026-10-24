package pl.jakubtworek.lld.promotion.domain;

/**
 * Podstawa rabatu jest osobną decyzją od kwalifikacji klienta/koszyka.
 */
public sealed interface DiscountScope {

    boolean includes(CartLine line);

    static DiscountScope all() {
        return new All();
    }

    static DiscountScope category(String name) {
        return new Category(name);
    }

    record All() implements DiscountScope {

        @Override
        public boolean includes(CartLine line) {
            return true;
        }
    }

    record Category(String name) implements DiscountScope {

        public Category {
            if (name == null || !name.matches("[A-Za-z0-9_-]+"))
                throw new IllegalArgumentException("Niepoprawna kategoria podstawy");
        }

        @Override
        public boolean includes(CartLine line) {
            return line.category().equals(name);
        }
    }
}
