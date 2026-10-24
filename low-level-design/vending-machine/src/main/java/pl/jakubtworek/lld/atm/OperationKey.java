package pl.jakubtworek.lld.atm;

import java.util.Objects;
import java.util.UUID;

/**
 * Klucz intencji wypłaty, rozróżniający wielkość liter, niezależny od UUID potwierdzenia i ID sesji.
 */
public record OperationKey(String value) {

    public OperationKey {
        value = Objects.requireNonNull(value, "value").strip();
        if (!value.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new IllegalArgumentException("Klucz: 1..64 znaków ASCII liter/cyfr lub ._:-");
        }
    }

    static OperationKey fresh() {
        return new OperationKey(UUID.randomUUID().toString());
    }
}
