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

        // quick check: same reference
        if (this == o) return true;

        // pattern matching for type check and cast
        if (!(o instanceof BadHashMoney m)) return false;

        // equality based on both fields
        return amount == m.amount &&
                currency.equals(m.currency);
    }

    @Override
    public int hashCode() {

        // Always returns the same hash value
        // All objects end up in the same bucket in hash-based collections

        // Causes heavy hash collisions
        // HashMap must compare objects using equals() for every insertion/search

        // Performance degrades from expected O(1) to O(n)

        return 1; // terrible implementation
    }
}