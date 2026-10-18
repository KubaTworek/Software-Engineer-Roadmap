package com.example.notification.domain;

import java.time.Instant;

/** Trwała decyzja, że konkretny contact point nie może być używany dla kanału. */
public record SuppressionEntry(String tenantId, Channel channel, String contactHash,
                               String reason, Instant createdAt) { }
