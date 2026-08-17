package com.ridesharing.mlmatching;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record MatchingRankRequest(
        @NotBlank String rideId,
        @NotBlank String cityId,
        String vehicleType,
        double surgeMultiplier,
        List<MatchingCandidate> candidates
) {}
