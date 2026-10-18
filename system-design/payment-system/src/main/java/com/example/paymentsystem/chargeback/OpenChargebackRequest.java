package com.example.paymentsystem.chargeback;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;

public record OpenChargebackRequest(@NotBlank String providerDisputeId, @Positive long amount, String reason) {
}
