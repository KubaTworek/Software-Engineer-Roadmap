package com.ridesharing.fraud;

import java.time.Instant;
import java.util.List;

public record RiskAssessmentResponse(
        String subjectId,
        String subjectType,
        int score,
        RiskDecision decision,
        List<String> reasons,
        Instant assessedAt
) {}
