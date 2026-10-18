package com.ridesharing.region;

import java.time.Instant;

/** Fencing epoch sprawia, że poprzedni właściciel nie może zapisywać po failoverze. */
public record RegionWriteLease(
        String aggregateId,
        String ownerRegion,
        long epoch,
        Instant expiresAt
) {}
