package pl.jakubtworek.marketplace.ordering.api;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Minimalny model semantyki Idempotency-Key dla POST. Rejestr wiąże klucz z
 * odciskiem requestu i wynikiem. Ten sam klucz + ten sam request zwraca poprzedni
 * rezultat; ten sam klucz + inny request jest konfliktem. Implementacja pamięciowa
 * jest granicą edukacyjną — w wielu replikach rejestr musi być trwały i współdzielony.
 */
@Component
@Profile("!postgres")
public class IdempotencyKeyRegistry {
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public Result execute(String key, String fingerprint, Supplier<UUID> operation) {
        if (key == null || key.isBlank()) {
            return new Result(operation.get(), false);
        }
        if (key.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key cannot exceed 128 characters");
        }
        AtomicBoolean created = new AtomicBoolean(false);
        Entry entry = entries.compute(key, (ignored, existing) -> {
            if (existing != null && !existing.fingerprint().equals(fingerprint)) {
                throw new IdempotencyKeyConflictException(key);
            }
            if (existing == null) {
                created.set(true);
                return new Entry(fingerprint, operation.get());
            }
            return existing;
        });
        return new Result(entry.result(), !created.get());
    }

    private record Entry(String fingerprint, UUID result) {}
    public record Result(UUID value, boolean replayed) {}

    protected static void validateKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Idempotency-Key cannot be blank");
        if (key.length() > 128) throw new IllegalArgumentException("Idempotency-Key cannot exceed 128 characters");
    }

    public static class IdempotencyKeyConflictException extends RuntimeException {
        public IdempotencyKeyConflictException(String key) {
            super("Idempotency-Key was already used with a different request: " + key);
        }
    }
}
