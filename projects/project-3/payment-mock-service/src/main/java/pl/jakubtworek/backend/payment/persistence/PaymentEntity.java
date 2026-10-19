package pl.jakubtworek.backend.payment.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pl.jakubtworek.backend.payment.api.PaymentRequest;
import pl.jakubtworek.backend.payment.api.PaymentResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Trwały ledger edukacyjnego providera. Stabilny klucz jest częścią rekordu efektu,
 * więc restart procesu nie usuwa wiedzy potrzebnej do bezpiecznego retry i lookupu.
 */
@Entity
@Table(name = "payments")
public class PaymentEntity {
    @Id private String idempotencyKey;
    private UUID paymentId;
    private UUID orderId;
    private String userId;
    private BigDecimal amount;
    private String status;
    private String refundIdempotencyKey;
    private Instant createdAt;
    private Instant refundedAt;

    protected PaymentEntity() { }

    private PaymentEntity(String key, PaymentRequest request, Instant now) {
        this.idempotencyKey = key;
        this.paymentId = UUID.randomUUID();
        this.orderId = request.orderId();
        this.userId = request.userId();
        this.amount = request.amount();
        this.status = "PAID";
        this.createdAt = now;
    }

    public static PaymentEntity paid(String key, PaymentRequest request, Instant now) {
        return new PaymentEntity(key, request, now);
    }

    public void requireSameRequest(PaymentRequest request) {
        if (!orderId.equals(request.orderId()) || !userId.equals(request.userId())
                || amount.compareTo(request.amount()) != 0) {
            throw new IllegalArgumentException("Idempotency-Key was reused with a different payment request");
        }
    }

    public void refund(String key, Instant now) {
        if (refundIdempotencyKey != null) {
            if (!refundIdempotencyKey.equals(key)) {
                throw new IllegalArgumentException("Payment was already refunded under a different operation key");
            }
            /* Dokładne powtórzenie nie zmienia nawet czasu efektu. Idempotencja oznacza
             * obserwowalnie ten sam wynik, a nie tylko brak drugiego przelewu. */
            return;
        }
        refundIdempotencyKey = key;
        status = "REFUNDED";
        refundedAt = now;
    }

    public PaymentResponse toResponse() { return new PaymentResponse(paymentId, orderId, status); }
}
