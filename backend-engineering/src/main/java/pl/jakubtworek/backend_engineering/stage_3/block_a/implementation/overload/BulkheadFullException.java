package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.overload;

public final class BulkheadFullException extends RuntimeException {

    public BulkheadFullException(String dependency) {
        super("bulkhead is full for dependency: " + dependency);
    }
}
