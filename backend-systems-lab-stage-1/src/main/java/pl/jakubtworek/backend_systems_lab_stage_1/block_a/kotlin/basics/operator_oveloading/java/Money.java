package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.operator_oveloading.java;

public class Money {

    private final double amount;
    private final String currency;

    public Money(double amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public Money add(Money other) {
        // Java does not support operator overloading.
        // Domain operations must be represented as methods.
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currencies must be the same");
        }

        return new Money(
                amount + other.amount,
                currency
        );
    }

    public double getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }
}