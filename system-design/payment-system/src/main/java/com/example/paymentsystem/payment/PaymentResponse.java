package com.example.paymentsystem.payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        UUID merchantId,
        String orderId,
        String customerId,
        long amount,
        long capturedAmount,
        long refundedAmount,
        String currency,
        Long settlementAmount,
        String settlementCurrency,
        String fxRate,
        long platformFeeAmount,
        long merchantAmount,
        String status,
        String provider,
        String providerPaymentId,
        String checkoutUrl,
        String captureMode,
        int riskScore,
        String riskDecision,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getPaymentId(), p.getMerchantId(), p.getOrderId(), p.getCustomerId(),
                p.getAmount(), p.getCapturedAmount(), p.getRefundedAmount(), p.getCurrency(),
                p.getSettlementAmount(), p.getSettlementCurrency(), p.getFxRate(),
                p.getPlatformFeeAmount(), p.getMerchantAmount(),
                p.getStatus().name(), p.getProvider().name(), p.getProviderPaymentId(), p.getCheckoutUrl(),
                p.getCaptureMode().name(), p.getRiskScore(), p.getRiskDecision().name(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
