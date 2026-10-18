package com.example.urlshortener.idempotency;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.service.ShortUrlService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Own transaction makes the idempotency marker and business effect atomic. */
@Service
public class IdempotentCreateTransaction {
    private final IdempotencyRecordRepository repository;
    private final ShortUrlService shortUrlService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration ttl;

    public IdempotentCreateTransaction(
            IdempotencyRecordRepository repository,
            ShortUrlService shortUrlService,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.idempotency.retention:PT24H}") Duration ttl
    ) {
        this.repository = repository;
        this.shortUrlService = shortUrlService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreateShortUrlResponse execute(String key, String fingerprint, CreateShortUrlRequest request) {
        Instant now = Instant.now(clock);
        IdempotencyRecord marker = new IdempotencyRecord(key, fingerprint, now, now.plus(ttl));
        // The unique primary key serializes concurrent requests using the same key.
        repository.saveAndFlush(marker);
        CreateShortUrlResponse response = shortUrlService.create(request);
        try {
            marker.complete(objectMapper.writeValueAsString(response));
            // Explicit flush makes the completed response part of the same durable unit of work.
            repository.saveAndFlush(marker);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot persist idempotent response", exception);
        }
        return response;
    }
}
