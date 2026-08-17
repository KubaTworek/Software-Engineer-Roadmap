package com.example.paymentsystem.chargeback;

import jakarta.validation.constraints.Positive;

public record OpenChargebackRequest(@Positive long amount, String reason) {
}
