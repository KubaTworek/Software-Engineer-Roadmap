package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

/**
 * Conceptual Redis-like eviction policies.
 */
public enum EvictionPolicy {

    /**
     * Good default for classic caches where a small hot set receives
     * most of the traffic.
     */
    ALL_KEYS_LRU,

    /**
     * Often better when hot keys are stable over time.
     */
    ALL_KEYS_LFU,

    /**
     * Useful only when keys consistently have TTL assigned.
     */
    VOLATILE_TTL,

    /**
     * Preserves data but makes new writes fail when memory is exhausted.
     * Usually a poor choice for a classic cache.
     */
    NO_EVICTION
}
