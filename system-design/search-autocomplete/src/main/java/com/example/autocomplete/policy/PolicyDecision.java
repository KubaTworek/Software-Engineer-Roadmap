package com.example.autocomplete.policy;

public record PolicyDecision(boolean allowed, String reason) {
    public static PolicyDecision allow() {
        return new PolicyDecision(true, "allowed");
    }

    public static PolicyDecision block(String r) {
        return new PolicyDecision(false, r);
    }
}
