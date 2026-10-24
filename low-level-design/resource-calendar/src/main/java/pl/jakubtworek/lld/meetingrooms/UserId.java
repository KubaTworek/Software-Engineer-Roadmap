package pl.jakubtworek.lld.meetingrooms;

import java.util.Locale;
import java.util.Objects;

/**
 * Deklarowany organizator, a nie mechanizm logowania. Osobny typ uniemożliwia
 * pomylenie kodu użytkownika z kodem sali w sygnaturze metody. Nie utrzymujemy
 * katalogu kont; każdy poprawny kod może zorganizować spotkanie.
 */
public record UserId(String value) {

    public UserId {
        value = Objects.requireNonNull(value, "value").strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9-]{1,32}")) {
            throw new IllegalArgumentException("Kod użytkownika: 1..32 liter ASCII, cyfr lub -");
        }
    }
}
