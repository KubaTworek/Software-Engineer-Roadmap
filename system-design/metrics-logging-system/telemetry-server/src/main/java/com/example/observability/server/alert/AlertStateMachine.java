package com.example.observability.server.alert;

import java.time.Duration;
import java.time.Instant;

/**
 * Stanowy model alertu eliminuje powiadomienie przy każdym ticku evaluatora.
 * Warunek musi utrzymać się przez `for`, zanim PENDING przejdzie w FIRING;
 * zanik warunku generuje pojedyncze przejście RESOLVED.
 */
public final class AlertStateMachine {
    public State next(State previous, boolean condition, Instant now, Duration forDuration) {
        if (!condition) {
            boolean resolved = previous != null && "FIRING".equals(previous.name());
            return new State(resolved ? "RESOLVED" : "INACTIVE", null, resolved);
        }
        Instant since = previous == null || previous.conditionSince() == null
                ? now : previous.conditionSince();
        boolean ready = !now.isBefore(since.plus(forDuration));
        String next = ready ? "FIRING" : "PENDING";
        boolean notification = ready && (previous == null || !"FIRING".equals(previous.name()));
        return new State(next, since, notification);
    }

    public record State(String name, Instant conditionSince, boolean notificationRequired) { }
}
