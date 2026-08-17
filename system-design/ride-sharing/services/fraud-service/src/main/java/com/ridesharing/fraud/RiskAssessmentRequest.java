package com.ridesharing.fraud;

import jakarta.validation.constraints.NotBlank;

public record RiskAssessmentRequest(
        @NotBlank String subjectId,
        @NotBlank String subjectType,
        String cityId,
        int cancellationCountLast24h,
        int paymentFailuresLast7d,
        int accountsOnDevice,
        double maxGpsJumpKmLast10m,
        boolean promoApplied
) {}
