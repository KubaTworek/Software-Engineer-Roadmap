package pl.jakubtworek.lld.parkinglot;

import java.util.Locale;
import java.util.Objects;

/**
 * Value object identyfikujący pojazd w tym ćwiczeniu. Normalizacja PRZED
 * porównywaniem powoduje, że " wx123 " i "WX123" nie tworzą dwóch aktywnych
 * biletów. Locale.ROOT usuwa zależność od języka systemu operacyjnego.
 * To uproszczony identyfikator, nie walidator przepisów rejestracji pojazdów.
 */
public record LicensePlate(String value) {

    public LicensePlate {
        value = Objects.requireNonNull(value, "value").strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9-]{1,20}")) {
            throw new IllegalArgumentException("Rejestracja: 1..20 znaków A-Z, cyfr lub myślnika");
        }
    }
}
