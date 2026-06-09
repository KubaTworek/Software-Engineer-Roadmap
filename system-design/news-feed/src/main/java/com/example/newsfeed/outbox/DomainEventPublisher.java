package com.example.newsfeed.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class DomainEventPublisher {

    public static final String POST_CREATED = "post.created";
    public static final String POST_DELETED = "post.deleted";
    public static final String POST_LIKED = "post.liked";
    public static final String POST_UNLIKED = "post.unliked";
    public static final String COMMENT_CREATED = "comment.created";
    public static final String COMMENT_DELETED = "comment.deleted";

    private final DomainEventRepository domainEventRepository;
    private final ObjectMapper objectMapper;

    public DomainEventPublisher(DomainEventRepository domainEventRepository, ObjectMapper objectMapper) {
        this.domainEventRepository = domainEventRepository;
        this.objectMapper = objectMapper;
    }

    public void publish(String eventType, UUID aggregateId, Map<String, Object> payload) {
        try {
            Instant now = Instant.now();
            String json = objectMapper.writeValueAsString(payload);
            DomainEvent event = new DomainEvent(UUID.randomUUID(), eventType, aggregateId, json, now);
            domainEventRepository.save(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize domain event payload.", exception);
        }
    }
}
