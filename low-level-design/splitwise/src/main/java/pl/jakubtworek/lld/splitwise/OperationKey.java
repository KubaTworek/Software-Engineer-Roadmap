package pl.jakubtworek.lld.splitwise;

import java.util.Objects;
import java.util.UUID;

/**
 * Tożsamość zamiaru zapisu, odrębna od ID wyniku w dzienniku. Klucz jest
 * wrażliwy na wielkość liter i obowiązuje w jednej instancji grupy.
 * Nowy klucz oznacza nową operację, nawet gdy dane są identyczne.
 */
public record OperationKey(String value) {

    public OperationKey {
        value = Objects.requireNonNull(value, "value").strip();
        if (!value.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new IllegalArgumentException("Klucz wymaga 1..64 znaków liter, cyfr lub ._:-");
        }
    }

    static OperationKey fresh() {
        return new OperationKey(UUID.randomUUID().toString());
    }
}
