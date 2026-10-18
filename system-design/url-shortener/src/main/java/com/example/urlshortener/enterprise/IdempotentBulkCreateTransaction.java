package com.example.urlshortener.enterprise;

import com.example.urlshortener.idempotency.IdempotencyRecord;
import com.example.urlshortener.idempotency.IdempotencyRecordRepository;
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

/** Keeps the bulk business effects and their replayable result in one SQL transaction. */
@Service
public class IdempotentBulkCreateTransaction {
    private final IdempotencyRecordRepository records;
    private final ShortUrlService urls;
    private final ObjectMapper json;
    private final Clock clock;
    private final Duration retention;

    public IdempotentBulkCreateTransaction(IdempotencyRecordRepository records, ShortUrlService urls,
            ObjectMapper json, Clock clock,
            @Value("${app.idempotency.retention:PT24H}") Duration retention) {
        this.records = records;
        this.urls = urls;
        this.json = json;
        this.clock = clock;
        this.retention = retention;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BulkCreateShortUrlResponse execute(String storageKey, String fingerprint,
            long ownerId, BulkCreateShortUrlRequest request) {
        Instant now = Instant.now(clock);
        IdempotencyRecord marker = new IdempotencyRecord(storageKey, fingerprint, now, now.plus(retention));
        records.saveAndFlush(marker);
        var created = request.urls().stream().map(item -> urls.create(item, ownerId)).toList();
        BulkCreateShortUrlResponse response = new BulkCreateShortUrlResponse(
                request.urls().size(), created.size(), created);
        try {
            marker.complete(json.writeValueAsString(response));
            records.saveAndFlush(marker);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot persist idempotent bulk response", failure);
        }
        return response;
    }
}
