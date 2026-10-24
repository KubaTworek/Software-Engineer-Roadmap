package pl.jakubtworek.lld.atm;

/**
 * Zamknięty katalog nominałów obsługiwanych przez kasetę ćwiczenia; liczności przechowuje osobny obiekt.
 */
public enum Banknote {

    PLN_10(1000), PLN_20(2000), PLN_50(5000), PLN_100(10000), PLN_200(20000);

    private final int grosz;

    Banknote(int grosz) {
        this.grosz = grosz;
    }

    public int grosz() {
        return grosz;
    }
}
