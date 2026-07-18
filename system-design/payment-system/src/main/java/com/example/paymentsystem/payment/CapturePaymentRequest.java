package com.example.paymentsystem.payment;

import jakarta.validation.constraints.Positive;

public record CapturePaymentRequest(@Positive long amount) {
}
