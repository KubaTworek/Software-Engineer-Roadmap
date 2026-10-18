package com.ridesharing.positioning;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class DriverPositioningServiceTest {

    @Test
    void unavailableForecastUsesConservativeDeterministicFallbackAndStableRanking() {
        // Port 1 jest celowo niedostępny: test dowodzi zachowania degradacyjnego,
        // a nie szczęśliwej ścieżki zależnej od uruchomionej usługi demand.
        var service = new DriverPositioningService(RestClient.builder(), "http://127.0.0.1:1");

        var response = service.recommend("driver-1", "warsaw", 52.23, 21.01);

        assertThat(response.recommendations()).hasSize(4);
        assertThat(response.recommendations().getFirst().h3Cell()).isEqualTo("downtown");
        assertThat(response.recommendations())
                .extracting(DriverPositioningRecommendation::repositionScore)
                .isSortedAccordingTo((left, right) -> Double.compare(right, left));
        assertThat(response.recommendations())
                .allSatisfy(recommendation -> {
                    assertThat(recommendation.expectedDemand()).isNotNegative();
                    assertThat(recommendation.repositionScore()).isNotNegative();
                });
    }
}
