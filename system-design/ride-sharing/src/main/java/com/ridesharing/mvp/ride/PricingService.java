package com.ridesharing.mvp.ride;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PricingService {
    public BigDecimal estimatePrice(double distanceKm, int durationMinutes) {
        var base = BigDecimal.valueOf(8.00);
        var distance = BigDecimal.valueOf(distanceKm).multiply(BigDecimal.valueOf(2.40));
        var time = BigDecimal.valueOf(durationMinutes).multiply(BigDecimal.valueOf(0.75));
        return base.add(distance).add(time).setScale(2, RoundingMode.HALF_UP);
    }
}
