package pl.jakubtworek.backend_engineering.stage_2.block_b.failure_semantics.payment;

/** A response proves either success or rejection; absence of a response proves neither. */
public record GatewayDecision(boolean authorized, String providerReference, String rejectionReason) {

    public GatewayDecision {
        if (authorized && (providerReference == null || providerReference.isBlank())) {
            throw new IllegalArgumentException("authorized payment requires providerReference");
        }
        if (authorized && rejectionReason != null) {
            throw new IllegalArgumentException("authorized payment cannot contain rejectionReason");
        }
        if (!authorized && (rejectionReason == null || rejectionReason.isBlank())) {
            throw new IllegalArgumentException("rejected payment requires rejectionReason");
        }
        if (!authorized && providerReference != null) {
            throw new IllegalArgumentException("rejected payment cannot contain providerReference");
        }
    }

    public static GatewayDecision authorized(String providerReference) {
        return new GatewayDecision(true, providerReference, null);
    }

    public static GatewayDecision rejected(String reason) {
        return new GatewayDecision(false, null, reason);
    }
}
