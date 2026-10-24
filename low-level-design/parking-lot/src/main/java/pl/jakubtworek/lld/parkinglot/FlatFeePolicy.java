package pl.jakubtworek.lld.parkinglot;

import java.util.Objects;

/**
 * Drugi rzeczywisty wariant wyceny: stała opłata także za postój zerowy.
 * Pokazuje sens interfejsu FeePolicy: zmiana cennika nie wymaga warunku if
 * w ParkingLot.exit. Poprawny przedział gwarantuje PricingContext, a parking
 * dodatkowo pilnuje globalnego porządku operacji. Brak użycia długości postoju
 * nie oznacza zgody na brak kontekstu lub ujemny przedział.
 */
public final class FlatFeePolicy implements FeePolicy {

    private final Money fee;

    public FlatFeePolicy(Money fee) {
        this.fee = Objects.requireNonNull(fee, "fee");
    }

    @Override
    public Money calculate(PricingContext context) {
        Objects.requireNonNull(context, "context");
        return fee;
    }
}
