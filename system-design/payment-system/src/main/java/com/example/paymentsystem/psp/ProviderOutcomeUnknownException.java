package com.example.paymentsystem.psp;

import java.util.UUID;

/** Timeout/transport failure where the remote side effect may already exist. */
public class ProviderOutcomeUnknownException extends ProviderUnavailableException {
    private final UUID paymentId;

    public ProviderOutcomeUnknownException(UUID paymentId) {
        super("Provider outcome is unknown for payment " + paymentId + "; do not create a second payment");
        this.paymentId = paymentId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }
}
