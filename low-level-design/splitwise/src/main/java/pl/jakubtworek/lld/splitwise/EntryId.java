package pl.jakubtworek.lld.splitwise;

import java.util.Objects;
import java.util.UUID;

/**
 * Tożsamość zapisu dziennika. Nowe polecenie tworzy nowy UUID, nawet jeśli ma
 * identyczne dane. To nie klucz idempotencji i nie dowód rzeczywistej płatności.
 */
public record EntryId(UUID value) {

    public EntryId {
        Objects.requireNonNull(value, "value");
    }

    public static EntryId parse(String value) {
        return new EntryId(UUID.fromString(Objects.requireNonNull(value, "value").strip()));
    }
}
