package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.comparing;

import java.util.Objects;

/**
 * Correct value object implementation.
 *
 * Rules:
 * 1. equals() and hashCode() must use same fields
 * 2. fields used should be immutable
 * 3. equals contract must be respected
 *
 * Contract of equals():
 *
 * - reflexive      x.equals(x)
 * - symmetric      x.equals(y) == y.equals(x)
 * - transitive     x.equals(y) && y.equals(z) → x.equals(z)
 * - consistent
 * - x.equals(null) == false
 *
 * hashCode contract:
 *
 * If two objects are equal according to equals(),
 * they MUST return the same hashCode().
 */
public final class Money {

    private final int amount;
    private final String currency;

    public Money(int amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public int getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;

        if (!(o instanceof Money money)) return false;

        return amount == money.amount &&
                currency.equals(money.currency);
    }

    @Override
    public int hashCode() {

        return Objects.hash(amount, currency);
    }
}