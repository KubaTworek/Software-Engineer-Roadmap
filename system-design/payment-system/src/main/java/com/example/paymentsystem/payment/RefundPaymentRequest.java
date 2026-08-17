package com.example.paymentsystem.payment;

import jakarta.validation.constraints.Positive;

public record RefundPaymentRequest(@Positive long amount, String reason) {
}
