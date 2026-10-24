package pl.jakubtworek.lld.atm;

import java.util.Locale;
import java.util.Objects;

/**
 * Umowny identyfikator karty, np. C1; nie przyjmujemy rzeczywistych danych karty ani numeru PAN.
 */
public record CardId(String value) {

    public CardId {
        value = Objects.requireNonNull(value, "value").strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9-]{1,32}")) {
            throw new IllegalArgumentException("Kod karty: 1..32 liter ASCII, cyfr lub -");
        }
    }
}
