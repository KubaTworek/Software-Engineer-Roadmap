package com.ridesharing.pricing;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {
    private final PricingService service = new PricingService();

    @Test
    void priceGrowsWithDemandButSurgeRemainsCapped() {
        var balanced = service.estimate(new PriceEstimateRequest("warsaw", "standard", 10.0, 20, 10, 10));
        var overloaded = service.estimate(new PriceEstimateRequest("warsaw", "standard", 10.0, 20, 10_000, 1));

        assertThat(overloaded.estimatedPrice()).isGreaterThan(balanced.estimatedPrice());
        assertThat(overloaded.surgeMultiplier()).isLessThanOrEqualTo(new BigDecimal("2.00"));
    }
}
