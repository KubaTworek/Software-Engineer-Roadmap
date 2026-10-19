package com.example.paymentsystem.payment;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "captured_amount", nullable = false)
    private long capturedAmount;

    @Column(name = "refunded_amount", nullable = false)
    private long refundedAmount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "settlement_amount")
    private Long settlementAmount;

    @Column(name = "settlement_currency")
    private String settlementCurrency;

    @Column(name = "fx_rate")
    private String fxRate;

    @Column(name = "platform_fee_amount", nullable = false)
    private long platformFeeAmount;

    @Column(name = "merchant_amount", nullable = false)
    private long merchantAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private PaymentProvider provider;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "checkout_url")
    private String checkoutUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_mode", nullable = false)
    private CaptureMode captureMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_decision", nullable = false)
    private RiskDecision riskDecision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Payment() {
    }

    public Payment(UUID merchantId, String orderId, String customerId, long amount, String currency,
                   CaptureMode captureMode, PaymentProvider provider, int riskScore, RiskDecision riskDecision) {
        this(merchantId, orderId, customerId, amount, currency, captureMode, PaymentMethod.CARD,
                provider, riskScore, riskDecision);
    }

    public Payment(UUID merchantId, String orderId, String customerId, long amount, String currency,
                   CaptureMode captureMode, PaymentMethod paymentMethod, PaymentProvider provider,
                   int riskScore, RiskDecision riskDecision) {
        Instant now = Instant.now();
        this.paymentId = UUID.randomUUID();
        this.merchantId = merchantId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.capturedAmount = 0;
        this.refundedAmount = 0;
        this.currency = currency;
        this.status = PaymentStatus.CREATED;
        this.provider = provider;
        this.captureMode = captureMode;
        this.paymentMethod = paymentMethod;
        this.riskScore = riskScore;
        this.riskDecision = riskDecision;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void attachProviderPayment(String providerPaymentId, String checkoutUrl) {
        require(PaymentStatus.PROCESSING);
        this.providerPaymentId = providerPaymentId;
        this.checkoutUrl = checkoutUrl;
        this.status = PaymentStatus.PENDING;
        touch();
    }

    public void startProviderAttempt() {
        require(PaymentStatus.CREATED);
        status = PaymentStatus.PROCESSING;
        touch();
    }

    /** A network failure does not prove that the provider rejected the charge. */
    public void markProviderOutcomeUnknown() {
        require(PaymentStatus.PROCESSING);
        status = PaymentStatus.UNKNOWN;
        touch();
    }

    /** Safe retry is allowed only when the adapter proves the request was not sent. */
    public void markProviderAttemptNotSent() {
        require(PaymentStatus.PROCESSING);
        // This local payment is terminal. A client retry may create a fresh
        // attempt because the adapter proved that no external effect exists.
        status = PaymentStatus.FAILED;
        touch();
    }

    /** Reconciliation/webhook may resolve an ambiguous create without repeating it. */
    public long reconcileSucceeded(String providerPaymentId) {
        if (!(status == PaymentStatus.UNKNOWN || status == PaymentStatus.PENDING)) {
            throw new PaymentException("Cannot reconcile success in status: " + status);
        }
        if (this.providerPaymentId == null) this.providerPaymentId = providerPaymentId;
        capturedAmount = amount;
        status = PaymentStatus.SUCCEEDED;
        touch();
        return amount;
    }

    public void applyFx(long settlementAmount, String settlementCurrency, String fxRate) {
        this.settlementAmount = settlementAmount;
        this.settlementCurrency = settlementCurrency;
        this.fxRate = fxRate;
    }

    public void applySplit(long platformFeeAmount, long merchantAmount) {
        this.platformFeeAmount = platformFeeAmount;
        this.merchantAmount = merchantAmount;
    }

    public long succeed() {
        require(PaymentStatus.PENDING);
        if (captureMode != CaptureMode.AUTOMATIC)
            throw new PaymentException("Manual payment requires authorization/capture");
        this.capturedAmount = amount;
        this.status = PaymentStatus.SUCCEEDED;
        touch();
        return amount;
    }

    public void authorize() {
        require(PaymentStatus.PENDING);
        if (captureMode != CaptureMode.MANUAL) throw new PaymentException("Only manual payments can be authorized");
        if (!paymentMethod.supportsManualCapture()) {
            throw new PaymentException("Payment method does not support manual capture: " + paymentMethod);
        }
        this.status = PaymentStatus.AUTHORIZED;
        touch();
    }

    public long capture(long captureAmount) {
        if (!(status == PaymentStatus.AUTHORIZED || status == PaymentStatus.PARTIALLY_CAPTURED)) {
            throw new PaymentException("Payment is not capturable in status: " + status);
        }
        long remainingAuthorization = amount - capturedAmount;
        if (captureAmount <= 0 || captureAmount > remainingAuthorization) {
            throw new PaymentException("Capture exceeds remaining authorization");
        }
        capturedAmount += captureAmount;
        status = capturedAmount == amount ? PaymentStatus.CAPTURED : PaymentStatus.PARTIALLY_CAPTURED;
        touch();
        return captureAmount;
    }

    public long refund(long refundAmount) {
        if (!(status == PaymentStatus.SUCCEEDED || status == PaymentStatus.CAPTURED
                || status == PaymentStatus.PARTIALLY_CAPTURED || status == PaymentStatus.PARTIALLY_REFUNDED)) {
            throw new PaymentException("Payment is not refundable in status: " + status);
        }
        long refundable = capturedAmount - refundedAmount;
        if (refundAmount <= 0 || refundAmount > refundable) throw new PaymentException("Invalid refund amount");
        refundedAmount += refundAmount;
        status = refundedAmount == capturedAmount ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        touch();
        return refundAmount;
    }

    public void cancel() {
        if (!(status == PaymentStatus.PENDING || status == PaymentStatus.AUTHORIZED))
            throw new PaymentException("Cannot cancel in status: " + status);
        status = PaymentStatus.CANCELED;
        touch();
    }

    public void openChargeback() {
        if (!(status == PaymentStatus.SUCCEEDED || status == PaymentStatus.CAPTURED || status == PaymentStatus.PARTIALLY_REFUNDED)) {
            throw new PaymentException("Cannot open chargeback in status: " + status);
        }
        status = PaymentStatus.CHARGEBACK_OPENED;
        touch();
    }

    public void loseChargeback() {
        require(PaymentStatus.CHARGEBACK_OPENED);
        status = PaymentStatus.CHARGEBACK_LOST;
        touch();
    }

    public void winChargeback() {
        require(PaymentStatus.CHARGEBACK_OPENED);
        status = PaymentStatus.CHARGEBACK_WON;
        touch();
    }

    private void require(PaymentStatus expected) {
        if (status != expected) throw new PaymentException("Expected status " + expected + " but was " + status);
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public long getAmount() {
        return amount;
    }

    public long getCapturedAmount() {
        return capturedAmount;
    }

    public long getRefundedAmount() {
        return refundedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Long getSettlementAmount() {
        return settlementAmount;
    }

    public String getSettlementCurrency() {
        return settlementCurrency;
    }

    public String getFxRate() {
        return fxRate;
    }

    public long getPlatformFeeAmount() {
        return platformFeeAmount;
    }

    public long getMerchantAmount() {
        return merchantAmount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public CaptureMode getCaptureMode() {
        return captureMode;
    }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }

    public int getRiskScore() {
        return riskScore;
    }

    public RiskDecision getRiskDecision() {
        return riskDecision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
