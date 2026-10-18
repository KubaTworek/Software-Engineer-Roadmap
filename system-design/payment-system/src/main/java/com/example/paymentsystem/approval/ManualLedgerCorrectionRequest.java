package com.example.paymentsystem.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record ManualLedgerCorrectionRequest(@NotNull UUID correctionId, @NotBlank String debitAccount,
                                            @NotBlank String creditAccount, @Positive long amount,
                                            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency) { }
