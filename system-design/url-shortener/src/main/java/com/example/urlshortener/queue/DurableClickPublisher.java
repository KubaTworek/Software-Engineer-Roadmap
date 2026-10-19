package com.example.urlshortener.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable mode pays for a PostgreSQL write on redirect so a broker outage cannot lose the click intent. */
@Service
public class DurableClickPublisher {
    private final ClickPublishOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DurableClickPublisher(ClickPublishOutboxRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void enqueue(ClickMessage message) {
        try {
            repository.save(new ClickPublishOutboxMessage(message.eventId(), objectMapper.writeValueAsString(message), Instant.now(clock)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize click event", exception);
        }
    }
}
