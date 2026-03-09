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

        if (this == o) return true;

        if (!(o instanceof BrokenMoney m)) return false;

        return amount == m.amount &&
                currency.equals(m.currency);
    }
}