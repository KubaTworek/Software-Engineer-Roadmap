package com.example.urlshortener.storage;

import java.util.Optional;

/**
 * Stage 5 abstraction for globally distributed URL lookups.
 *
 * The local implementation is JPA/PostgreSQL. In production, the same contract
 * can be backed by DynamoDB global tables, Cassandra/Scylla, CockroachDB,
 * Spanner or another globally replicated key-value store.
 */
public interface DistributedUrlStore {
    Optional<UrlLookupRecord> findByShortCode(String shortCode);
    void upsert(UrlLookupRecord record);
    void delete(String shortCode);
}
