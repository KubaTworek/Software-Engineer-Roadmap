package com.example.paymentsystem.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OutboxService {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void save(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        try {
            repository.save(new OutboxEvent(aggregateType, aggregateId, eventType, objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
