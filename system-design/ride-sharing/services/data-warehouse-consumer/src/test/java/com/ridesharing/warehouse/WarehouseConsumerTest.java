package com.ridesharing.warehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WarehouseConsumerTest {
    @Test
    void eventIdIsRequiredAndInsertIsIdempotent() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var consumer = new WarehouseConsumer(jdbc, new ObjectMapper());
        var event = Map.<String, Object>of(
                "eventId", "event-1",
                "eventType", "RideRequested",
                "aggregateId", "ride-1",
                "payload", Map.of("cityId", "warsaw")
        );

        consumer.consume(event, "ride.events");

        verify(jdbc).update(contains("ON CONFLICT (event_id) DO NOTHING"),
                eq("event-1"), eq("ride.events"), eq("RideRequested"), eq("ride-1"), eq("warsaw"), anyString());
        assertThatThrownBy(() -> consumer.consume(Map.of("eventType", "legacy"), "ride.events"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
