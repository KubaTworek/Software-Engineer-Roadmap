package pl.jakubtworek.marketplace.process;

public final class ConcurrentProcessModificationException extends RuntimeException {
    public ConcurrentProcessModificationException(java.util.UUID orderId) {
        super("Order fulfillment process was concurrently modified: " + orderId);
    }
}
