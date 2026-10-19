package com.ridesharing.positioning;

/** Minimalny kontrakt Demand Service wykorzystywany przez positioning. */
public record DemandForecast(double expectedRequests, double confidence) {
}
