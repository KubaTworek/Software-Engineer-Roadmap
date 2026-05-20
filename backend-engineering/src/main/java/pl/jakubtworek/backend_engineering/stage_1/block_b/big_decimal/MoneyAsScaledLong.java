package pl.jakubtworek.backend_engineering.stage_1.block_b.big_decimal;

public final class MoneyAsScaledLong {

    private final long cents;

    private MoneyAsScaledLong(long cents) {
        // The internal representation is a scaled integer.
        // This is allocation-free during arithmetic when used as primitive long values,
        // but this wrapper itself is still an object.
        this.cents = cents;
    }

    public static MoneyAsScaledLong ofCents(long cents) {
        return new MoneyAsScaledLong(cents);
    }

    public long cents() {
        return cents;
    }

    public MoneyAsScaledLong plus(MoneyAsScaledLong other) {
        // This immutable wrapper still allocates a new object for every plus() call.
        // It is useful for domain modeling, but it is not allocation-free in a hot loop.
        return new MoneyAsScaledLong(this.cents + other.cents);
    }
}