package pl.jakubtworek.lld.cache.domain;

import java.util.List;

/**
 * Resize najpierw wygasza, potem wybiera ofiary. Listy rozdzielają przyczyny:
 * expired według deadline (remisy bez gwarancji), evicted według polityki.
 * Kolekcje są kopiowane; generyczne wartości pozostają współdzielonymi referencjami.
 */
public record ResizeResult<K, V>(int previousCapacity, int capacity, List<RemovedEntry<K, V>> expired,
        List<RemovedEntry<K, V>> evicted) {

    public ResizeResult {
        if (previousCapacity < 1 || capacity < 1)
            throw new IllegalArgumentException("Pojemność musi być dodatnia");
        expired = List.copyOf(expired);
        evicted = List.copyOf(evicted);
    }
}
