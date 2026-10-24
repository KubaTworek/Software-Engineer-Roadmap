package pl.jakubtworek.lld.cache.domain;

/**
 * Wybór indeksu ofiary: LRU używa głowy LinkedHashMap, LFU pierwszego kubełka
 * FrequencyIndex. Oba wybory są średnio O(1), bez przeglądania całego cache.
 */
public enum EvictionPolicy {

    LRU, LFU
}
