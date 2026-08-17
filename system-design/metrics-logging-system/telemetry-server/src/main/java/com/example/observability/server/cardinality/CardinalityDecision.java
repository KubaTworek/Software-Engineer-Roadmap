package com.example.observability.server.cardinality;

public record CardinalityDecision(boolean accepted, String reason, int droppedSeries, int acceptedSeries) {
    public static CardinalityDecision accepted(int acceptedSeries) {
        return new CardinalityDecision(true, "accepted", 0, acceptedSeries);
    }

    public static CardinalityDecision rejected(String reason, int droppedSeries, int acceptedSeries) {
        return new CardinalityDecision(false, reason, droppedSeries, acceptedSeries);
    }
}
