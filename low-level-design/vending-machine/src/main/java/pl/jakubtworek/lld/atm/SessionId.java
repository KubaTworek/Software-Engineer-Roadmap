package pl.jakubtworek.lld.atm;

import java.util.Objects;
import java.util.UUID;

/**
 * Tożsamość konkretnego włożenia karty. Ten sam użytkownik i karta włożona ponownie dostają inne ID.
 */
public record SessionId(UUID value) {

    public SessionId {
        Objects.requireNonNull(value, "value");
    }

    public static SessionId parse(String text) {
        return new SessionId(UUID.fromString(Objects.requireNonNull(text, "text").strip()));
    }
}
