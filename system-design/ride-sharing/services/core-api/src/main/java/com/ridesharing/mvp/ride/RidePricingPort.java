package com.ridesharing.mvp.ride;

import java.math.BigDecimal;

/** Granica wyceny przejazdu; profil rozproszony nie omija Pricing Service. */
public interface RidePricingPort {
    BigDecimal estimatePrice(String cityId, String vehicleType, double distanceKm, int durationMinutes);
}
