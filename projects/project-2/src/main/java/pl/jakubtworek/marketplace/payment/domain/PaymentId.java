package pl.jakubtworek.marketplace.payment.domain;

import java.util.UUID;

public record PaymentId(UUID value) {
    public static PaymentId newId() { return new PaymentId(UUID.randomUUID()); }
}
