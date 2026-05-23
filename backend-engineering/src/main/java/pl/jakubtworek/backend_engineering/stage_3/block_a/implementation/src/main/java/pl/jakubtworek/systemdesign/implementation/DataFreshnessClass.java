package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Describes how fresh a class of data must be.
 */
public enum DataFreshnessClass {
    STRICTLY_FRESH,
    SHORT_STALE_ACCEPTABLE,
    EVENTUALLY_CONSISTENT,
    MOSTLY_STATIC
}
