package com.example.videostreaming.premium;

public enum SubscriptionPlanCode {
    FREE(0),
    BASIC(10),
    PREMIUM(20);

    private final int level;
    SubscriptionPlanCode(int level) { this.level = level; }
    public int level() { return level; }

    public boolean satisfies(SubscriptionPlanCode required) {
        return this.level >= required.level;
    }
}
