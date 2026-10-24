package pl.jakubtworek.lld.promotion.domain;

import java.util.HashSet;
import java.util.List;

/**
 * Niemutowalny koszyk i jego sprawdzona suma. Kopia listy chroni strukturę,
 * a CartLine/Money są niemutowalne. Duplikat SKU jest odmową, bo dwa różne
 * opisy/ceny tego samego SKU wymagałyby osobnej reguły scalania.
 */
public final class Basket {

    private final List<CartLine> lines;
    private final Money subtotal;

    public Basket(List<CartLine> lines) {
        this.lines = List.copyOf(lines);
        var seen = new HashSet<String>();
        Money sum = Money.ZERO;
        for (CartLine line : this.lines) {
            if (!seen.add(line.sku()))
                throw new IllegalArgumentException("Powtórzone SKU: " + line.sku());
            sum = sum.plus(line.total());
        }
        subtotal = sum;
    }

    public List<CartLine> lines() {
        return lines;
    }

    public Money subtotal() {
        return subtotal;
    }

    public boolean containsCategory(String category) {
        return lines.stream().anyMatch(l -> l.category().equals(category));
    }
}
