package com.example.notification.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DigestBuffer {
    private final UUID id;
    private final String tenantId;
    private final String userId;
    private final String digestKey;
    private final List<Map<String, Object>> items = new ArrayList<>();
    private final Instant flushAt;
    private boolean flushed;
    private boolean claimed;

    public DigestBuffer(UUID id, String tenantId, String userId, String digestKey, Instant flushAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.digestKey = digestKey;
        this.flushAt = flushAt;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getDigestKey() { return digestKey; }
    public List<Map<String, Object>> getItems() { return List.copyOf(items); }
    public Instant getFlushAt() { return flushAt; }
    public boolean isFlushed() { return flushed; }
    public boolean isClaimed() { return claimed; }

    public synchronized void addItem(Map<String, Object> item) {
        if (flushed || claimed) throw new IllegalStateException("Digest is already being flushed");
        items.add(Map.copyOf(item));
    }
    public synchronized boolean tryClaim() {
        if (flushed || claimed) return false;
        claimed = true;
        return true;
    }
    public synchronized void markFlushed() { this.flushed = true; this.claimed = false; }
    public synchronized void releaseClaim() { this.claimed = false; }
    public static DigestBuffer restore(UUID id, String tenantId, String userId, String key, Instant flushAt,
                                       List<Map<String, Object>> items, boolean flushed, boolean claimed) {
        DigestBuffer buffer = new DigestBuffer(id, tenantId, userId, key, flushAt);
        buffer.items.addAll(items.stream().map(Map::copyOf).toList());
        buffer.flushed = flushed; buffer.claimed = claimed;
        return buffer;
    }
}
