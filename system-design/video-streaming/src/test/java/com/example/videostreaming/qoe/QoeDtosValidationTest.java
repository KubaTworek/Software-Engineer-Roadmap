package com.example.videostreaming.qoe;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QoeDtosValidationTest {
    @Test
    void rejectsUnknownEventNegativeMeasurementsAndInvalidCountry() {
        var request = new QoeDtos.IngestQoeRequest(
                UUID.randomUUID(), UUID.randomUUID(), "session", "invented_event",
                -1, -2, -3, "cdn", "player", "mobile", "POL", Map.of(), Instant.now());

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator().validate(request);
            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                    .contains("eventType", "startupTimeMs", "rebufferTimeMs", "bitrateKbps", "country");
        }
    }
}
