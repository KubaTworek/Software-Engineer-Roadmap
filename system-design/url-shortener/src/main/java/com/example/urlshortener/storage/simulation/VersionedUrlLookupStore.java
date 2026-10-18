package com.example.urlshortener.storage.simulation;

import com.example.urlshortener.storage.UrlLookupRecord;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic multi-region simulation. Delivery may be duplicated or reordered,
 * but a record with a lower source version can never overwrite newer state.
 * This is correct only for the documented single-write-region model.
 */
public class VersionedUrlLookupStore {
    private final Map<String, UrlLookupRecord> records = new ConcurrentHashMap<>();

    public void apply(UrlLookupRecord candidate) {
        records.compute(candidate.shortCode(), (key, current) ->
                current == null || candidate.version() > current.version() ? candidate : current);
    }

    public Optional<UrlLookupRecord> find(String shortCode) {
        return Optional.ofNullable(records.get(shortCode));
    }
}
