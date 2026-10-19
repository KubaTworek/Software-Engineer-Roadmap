package com.ridesharing.mleta;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EtaModelServiceTest {
    @Test
    void heavierTrafficCannotProduceShorterEta() {
        var service = new EtaModelService();
        var base = new EtaPredictionRequest(
                "warsaw", "standard", new Coordinate(52.23, 21.01),
                new Coordinate(52.30, 21.10), 10.0, 12, 2, 1.0, 1.0
        );
        var congested = new EtaPredictionRequest(
                "warsaw", "standard", base.origin(), base.destination(),
                10.0, 12, 2, 2.0, 1.0
        );

        assertThat(service.predict(congested).etaMinutes())
                .isGreaterThan(service.predict(base).etaMinutes());
    }
}
