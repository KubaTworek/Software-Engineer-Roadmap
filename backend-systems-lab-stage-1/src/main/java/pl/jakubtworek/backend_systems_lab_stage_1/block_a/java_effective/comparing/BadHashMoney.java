package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.comparing;

/**
 * Extremely bad hashCode implementation.
 *
 * Always returns same value.
 *
 * Consequence:
 * HashMap degenerates into linked list → O(n).
 */
public class BadHashMoney {

    private final int amount;
    private final String currency;

    public BadHashMoney(int amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;

        if (!(o instanceof BadHashMoney m)) return false;

        return amount == m.amount &&
                currency.equals(m.currency);
    }

    @Override
    public int hashCode() {
        return 1; // terrible
    }
}