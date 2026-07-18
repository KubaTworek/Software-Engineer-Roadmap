package com.example.paymentsystem.merchant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateMerchantRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String settlementCurrency
) {
}
