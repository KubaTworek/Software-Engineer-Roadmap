package pl.jakubtworek.marketplace.payment.application;

import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.UUID;

public interface PaymentGateway {
    PaymentReservationResult reserve(UUID orderId, Money amount);

    /** Lookup nie powtarza obciążenia karty; pyta providera o skutek tej samej operacji. */
    default PaymentReservationResult lookup(String providerOperationId) {
        return new PaymentReservationResult(Outcome.UNKNOWN, providerOperationId, "lookup unsupported");
    }

    enum Outcome { ACCEPTED, REJECTED, UNKNOWN }

    record PaymentReservationResult(Outcome outcome, String providerOperationId, String reason) {
        public PaymentReservationResult(boolean accepted, String reason) {
            this(accepted ? Outcome.ACCEPTED : Outcome.REJECTED, null, reason);
        }
        public boolean accepted() { return outcome == Outcome.ACCEPTED; }
    }
}
