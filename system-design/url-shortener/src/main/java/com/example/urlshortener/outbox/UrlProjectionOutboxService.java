package com.example.urlshortener.outbox;

import com.example.urlshortener.service.UrlProjectionChanged;
import com.example.urlshortener.storage.UrlLookupRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** Writes the projection intent in the same transaction as the URL source row. */
@Service
public class UrlProjectionOutboxService {
    private final UrlProjectionOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UrlProjectionOutboxService(UrlProjectionOutboxRepository repository, ObjectMapper objectMapper, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.events = events;
        this.clock = clock;
    }

    public void enqueue(UrlLookupRecord record) {
        String eventId = UUID.randomUUID().toString();
        try {
            repository.save(new UrlProjectionOutboxMessage(eventId, objectMapper.writeValueAsString(record), Instant.now(clock)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize URL projection", exception);
        }
        events.publishEvent(new UrlProjectionChanged(eventId, record));
    }
}
