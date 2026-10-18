package com.ridesharing.mvp.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OutboxEnvelopeTest {
    @Test
    void persistedPayloadContainsDeduplicationAndEvolutionMetadata() throws Exception {
        var repository = mock(OutboxEventRepository.class);
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new OutboxService(repository, mapper);
        ReflectionTestUtils.setField(service, "rideEventsTopic", "ride.events");

        UUID rideId = UUID.randomUUID();
        service.rideEvent(rideId, "RideRequested", Map.of("rideId", rideId.toString()));

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        var row = captor.getValue();
        var json = mapper.readTree(row.getPayload());
        assertThat(json.get("eventId").asText()).isEqualTo(row.getId().toString());
        assertThat(json.get("eventType").asText()).isEqualTo("RideRequested");
        assertThat(json.get("aggregateId").asText()).isEqualTo(rideId.toString());
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("payload").get("rideId").asText()).isEqualTo(rideId.toString());
    }
}
