package pl.jakubtworek.lld.atm;

import java.util.Locale;
import java.util.Objects;

/**
 * Kod konta ćwiczenia, nie numer rachunku bankowego. Osobny typ chroni przed pomyleniem konta z kartą.
 */
public record AccountId(String value) {

    public AccountId {
        value = Objects.requireNonNull(value, "value").strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9-]{1,32}")) {
            throw new IllegalArgumentException("Kod konta: 1..32 liter ASCII, cyfr lub -");
        }
    }
}
