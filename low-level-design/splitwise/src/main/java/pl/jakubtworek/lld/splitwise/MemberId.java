package pl.jakubtworek.lld.splitwise;

import java.util.Locale;
import java.util.Objects;

/**
 * Kod uczestnika nie jest nazwiskiem ani uwierzytelnieniem. Normalizacja ROOT
 * daje tę samą tożsamość niezależnie od języka komputera; kod rozstrzyga remisy
 * przy groszowych resztach, dlatego jego porządek jest częścią kontraktu.
 */
public record MemberId(String value) {

    public MemberId {
        value = Objects.requireNonNull(value, "value").strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9-]{1,32}")) {
            throw new IllegalArgumentException("Kod wymaga 1..32 znaków A-Z, cyfr lub myślnika");
        }
    }
}
