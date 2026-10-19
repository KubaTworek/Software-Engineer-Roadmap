package com.ridesharing.dynamicpricing;

/** Minimalny, typowany kontrakt odpowiedzi potrzebny przez pricing. */
public record DemandForecast(double expectedRequests, double confidence) {
}
