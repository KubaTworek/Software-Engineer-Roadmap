package com.example.autocomplete.rollout;

import java.time.Instant;

public record IndexDeployment(
        String version,
        String checksum,
        int suggestionCount,
        Instant registeredAt
) {}
