package com.example.urlshortener.service;

import com.example.urlshortener.storage.DistributedStorageReplicationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.example.urlshortener.observability.UrlShortenerMetrics;
import java.time.Duration;
import java.time.Instant;
import com.example.urlshortener.outbox.UrlProjectionOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.urlshortener.edge.EdgeCacheInvalidator;
import com.example.urlshortener.model.UrlStatus;

/** Updates derived lookup stores only after PostgreSQL has committed the source change. */
@Component
public class UrlProjectionUpdater {
    private static final Logger log = LoggerFactory.getLogger(UrlProjectionUpdater.class);
    private final ShortUrlCacheService cache;
    private final DistributedStorageReplicationService replication;
    private final UrlShortenerMetrics metrics;
    private final UrlProjectionOutboxRepository outbox;
    private final EdgeCacheInvalidator edge;

    public UrlProjectionUpdater(ShortUrlCacheService cache, DistributedStorageReplicationService replication,
            UrlShortenerMetrics metrics, UrlProjectionOutboxRepository outbox, EdgeCacheInvalidator edge) {
        this.cache = cache;
        this.replication = replication;
        this.metrics = metrics;
        this.outbox = outbox;
        this.edge = edge;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void update(UrlProjectionChanged event) {
        deliver(event.eventId(), event.record());
    }

    public boolean deliver(String eventId, com.example.urlshortener.storage.UrlLookupRecord record) {
        // A BLOCKED record acts as a tombstone and wins over a previously cached ACTIVE record.
        try {
            if (!cache.put(record)) {
                throw new IllegalStateException("Redis projection was not updated");
            }
            replication.publishUpsert(record);
            if (record.status() != UrlStatus.ACTIVE) edge.purge(record.shortCode());
            outbox.markSent(eventId, Instant.now());
            if (record.updatedAt() != null) {
                metrics.projectionLag(Duration.between(record.updatedAt(), Instant.now()).abs());
            }
            return true;
        } catch (RuntimeException exception) {
            metrics.projectionFailure();
            log.warn("Projection delivery failed eventId={}; durable outbox will retry", eventId, exception);
            return false;
        }
    }
}
