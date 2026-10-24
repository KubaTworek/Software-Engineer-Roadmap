package pl.jakubtworek.lld.elevators;

import java.util.Locale;
import java.util.Objects;

/**
 * Tożsamość kabiny nie zależy od piętra ani trybu. E1 i e1 oznaczają tę samą windę; remisy rozstrzyga kod leksykograficzny.
 */
public record ElevatorId(String value) {

    public ElevatorId {
        value = Objects.requireNonNull(value, "value").strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9-]{1,32}")) {
            throw new IllegalArgumentException("Kod windy: 1..32 liter ASCII, cyfr lub -");
        }
    }
}
