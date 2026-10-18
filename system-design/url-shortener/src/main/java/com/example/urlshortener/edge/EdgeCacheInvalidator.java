package com.example.urlshortener.edge;

/** Production boundary implemented by a CDN-specific purge API. */
public interface EdgeCacheInvalidator {
    void purge(String shortCode);
}
