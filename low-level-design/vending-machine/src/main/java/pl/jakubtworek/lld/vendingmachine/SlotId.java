package pl.jakubtworek.lld.vendingmachine;

import java.util.Locale;
import java.util.Objects;

/**
 * Value object wyboru, np. A1. Normalizacja odbywa się przed porównaniem,
 * więc " a1 " i "A1" nie tworzą dwóch lokalizacji tego samego przycisku.
 * Unikalność ID całej konfiguracji sprawdza automat, nie pojedyncza wartość.
 */
public record SlotId(String value) {

    public SlotId {
        value = Objects.requireNonNull(value, "value").strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9]{1,8}")) {
            throw new IllegalArgumentException("Kod produktu: 1..8 liter A-Z lub cyfr");
        }
    }
}
