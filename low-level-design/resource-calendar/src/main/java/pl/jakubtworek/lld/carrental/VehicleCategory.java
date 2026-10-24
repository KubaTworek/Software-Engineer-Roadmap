package pl.jakubtworek.lld.carrental;

/**
 * Dwie stawki są parametrami jednego algorytmu. Nie uzasadniają podklas auta ani Strategy.
 */
public enum VehicleCategory {

    ECONOMY(new Money(10_000)), SUV(new Money(20_000));

    private final Money dailyRate;

    VehicleCategory(Money dailyRate) {
        this.dailyRate = dailyRate;
    }

    public Money dailyRate() {
        return dailyRate;
    }
}
