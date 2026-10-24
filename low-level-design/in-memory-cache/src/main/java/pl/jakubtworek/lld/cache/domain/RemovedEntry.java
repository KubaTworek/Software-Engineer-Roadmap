package pl.jakubtworek.lld.cache.domain;

import java.util.Objects;

/**
 * Potwierdzenie usunięcia: klucz i wartość, bez mutowalnego uchwytu do wpisu.
 */
public record RemovedEntry<K, V>(K key, V value) {

    public RemovedEntry {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
    }
}
