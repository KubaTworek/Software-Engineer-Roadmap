package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Common types of remote calls.
 */
public enum RemoteCallType {
    DATABASE,
    REDIS,
    PAYMENT_API,
    SEARCH_API,
    RECOMMENDATION_API,
    THIRD_PARTY_API,
    INTERNAL_SERVICE
}
