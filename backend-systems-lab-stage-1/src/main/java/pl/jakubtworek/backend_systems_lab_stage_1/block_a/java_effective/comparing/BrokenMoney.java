package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.comparing;

/**
 * Broken implementation.
 *
 * equals() overridden
 * but hashCode() NOT overridden.
 *
 * This breaks HashMap / HashSet behaviour.
 */
public class BrokenMoney {

    private final int amount;
    private final String currency;

    public BrokenMoney(int amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    @Override
    public boolean equals(Object o) {

        // quick reference check
        if (this == o) return true;

        // type check with pattern matching
        if (!(o instanceof BrokenMoney m)) return false;

        // equality defined by both fields
        return amount == m.amount &&
                currency.equals(m.currency);
    }

    // hashCode() is missing

    // Because of this, the class inherits Object.hashCode(),
    // which typically returns a value based on object identity (memory address)

    // Two objects that are equal according to equals()
    // may produce different hash codes

    // In hash-based collections (HashMap, HashSet):
    // equal objects may be stored in different buckets

    // As a result, lookups using logically equal keys may fail
}