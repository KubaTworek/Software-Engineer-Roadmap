package pl.jakubtworek.lld.meetingrooms;

import java.util.Locale;
import java.util.Objects;

/**
 * Kod sali jest tożsamością, niezależną od nazwy i pojemności. Normalizacja
 * zapobiega traktowaniu A1 i a1 jako dwóch zasobów. Kolejność kodów jest
 * leksykograficzna: A10 wystąpi przed A2, nie stosujemy sortowania naturalnego.
 */
public record RoomId(String value) {

    public RoomId {
        value = Objects.requireNonNull(value, "value").strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9-]{1,32}")) {
            throw new IllegalArgumentException("Kod sali: 1..32 liter ASCII, cyfr lub -");
        }
    }
}
