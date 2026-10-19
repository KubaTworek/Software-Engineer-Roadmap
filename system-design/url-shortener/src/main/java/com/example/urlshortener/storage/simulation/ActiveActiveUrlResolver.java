package com.example.urlshortener.storage.simulation;

import com.example.urlshortener.model.UrlStatus;

/**
 * Deterministic conflict model for education, not a datastore adapter.
 * Safety-sensitive tombstones dominate ACTIVE at the same logical revision;
 * otherwise the Lamport counter and region id provide a total deterministic order.
 */
public class ActiveActiveUrlResolver {
    public VersionedValue resolve(VersionedValue left, VersionedValue right) {
        int revision = Long.compare(left.counter(), right.counter());
        if (revision != 0) return revision > 0 ? left : right;
        if (left.status() != right.status()) {
            if (left.status() != UrlStatus.ACTIVE) return left;
            if (right.status() != UrlStatus.ACTIVE) return right;
        }
        return left.regionId().compareTo(right.regionId()) >= 0 ? left : right;
    }

    public record VersionedValue(String shortCode, String longUrl, UrlStatus status, long counter, String regionId) {}
}
