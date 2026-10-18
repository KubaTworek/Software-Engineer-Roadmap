package com.ridesharing.demand;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DemandEventConsumerTest {

    private final DemandPredictionService service = mock(DemandPredictionService.class);
    private final DemandEventConsumer consumer = new DemandEventConsumer(service, new ObjectMapper());

    @Test
    void readsSignalOnlyFromVersionedEnvelopePayload() {
        consumer.consume("""
                {"eventId":"e1","eventType":"RideRequested","schemaVersion":1,
                 "payload":{"cityId":"warsaw","h3Cell":"891e2040883ffff"}}
                """);

        verify(service).recordRideRequested("warsaw", "891e2040883ffff");
    }

    @Test
    void unrelatedAndMalformedEventsDoNotBecomeDemand() {
        consumer.consume("""
                {"eventType":"RideCompleted","payload":{"cityId":"warsaw","h3Cell":"cell"}}
                """);
        verifyNoInteractions(service);

        assertThatThrownBy(() -> consumer.consume("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid domain event envelope");
        verifyNoInteractions(service);
    }
}
