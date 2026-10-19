package com.ridesharing.dynamicpricing;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicPricingGuardrailTest {
    @Test
    void extremeDemandCannotExceedConfiguredSurgeCap() {
        var service = new DynamicPricingService(RestClient.builder(), "http://127.0.0.1:1");
        var result = service.price(new DynamicPriceRequest(
                "warsaw", "cell-a", "standard", 10, 20,
                100_000, 0, new BigDecimal("40.00")
        ));

        assertThat(result.surgeMultiplier()).isEqualByComparingTo("2.25");
        assertThat(result.guardrail()).isEqualTo("surge_cap_applied");
    }
}
