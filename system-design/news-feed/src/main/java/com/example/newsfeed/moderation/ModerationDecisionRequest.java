package com.example.newsfeed.moderation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModerationDecisionRequest(
        @NotNull Decision decision,
        @Size(max = 1000) String reason
) {
    public enum Decision { APPROVE, REJECT }
}
