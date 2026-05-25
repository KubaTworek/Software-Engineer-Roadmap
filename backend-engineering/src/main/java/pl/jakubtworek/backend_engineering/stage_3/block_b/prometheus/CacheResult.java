package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

/**
 * Represents a bounded set of cache outcomes.
 *
 * Do not encode cache keys or entity identifiers into labels.
 */
public enum CacheResult {

    HIT("hit"),
    MISS("miss"),
    ERROR("error"),
    TIMEOUT("timeout");

    private final String labelValue;

    CacheResult(String labelValue) {
        this.labelValue = labelValue;
    }

    public String labelValue() {
        return labelValue;
    }
}