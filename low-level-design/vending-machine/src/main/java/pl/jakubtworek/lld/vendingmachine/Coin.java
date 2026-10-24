package pl.jakubtworek.lld.vendingmachine;

import java.util.Arrays;

/**
 * Obsługiwane nominały monet, bez banknotów i kart. Enum zamyka zbiór
 * dozwolonych wartości: liczba 30 groszy może być ceną, lecz nie jest monetą.
 * Nie używamy ordinal() jako kwoty; przestawienie deklaracji nie zmienia nominału.
 */
public enum Coin {

    GR1(1),
    GR2(2),
    GR5(5),
    GR10(10),
    GR20(20),
    GR50(50),
    PLN1(100),
    PLN2(200),
    PLN5(500);

    private final int grosz;

    Coin(int grosz) {
        this.grosz = grosz;
    }

    public int grosz() {
        return grosz;
    }

    public static Coin fromGrosz(int grosz) {
        return Arrays.stream(values()).filter(coin -> coin.grosz == grosz).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Nieobsługiwany nominał: " + grosz));
    }
}
