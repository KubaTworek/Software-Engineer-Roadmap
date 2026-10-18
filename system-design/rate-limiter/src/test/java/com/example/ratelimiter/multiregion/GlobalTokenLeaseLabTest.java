package com.example.ratelimiter.multiregion;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GlobalTokenLeaseLabTest {
    @Test
    void centralPartitionCannotCreateMoreExposureThanOutstandingLeases() {
        Instant now = Instant.parse("2026-09-14T10:00:00Z");
        GlobalTokenLeaseLab allocator = new GlobalTokenLeaseLab(100);
        var eu = allocator.grant("eu", 40, now, now.plusSeconds(30));
        var us = allocator.grant("us", 40, now, now.plusSeconds(30));

        for (int i = 0; i < 15; i++) assertThat(allocator.consume("eu", eu.fencingToken(), 1, now)).isTrue();
        for (int i = 0; i < 10; i++) assertThat(allocator.consume("us", us.fencingToken(), 1, now)).isTrue();

        // Jeśli allocator jest teraz nieosiągalny, regiony mogą wydać najwyżej
        // pozostałe 55 tokenów; partycja nie produkuje nieograniczonego budżetu.
        assertThat(allocator.outstandingExposure()).isEqualTo(55);
        assertThat(allocator.unleased()).isEqualTo(20);
    }

    @Test
    void fencingRejectsDelayedOwnerAfterLeaseExpiryAndRegrant() {
        Instant now = Instant.parse("2026-09-14T10:00:00Z");
        GlobalTokenLeaseLab allocator = new GlobalTokenLeaseLab(10);
        var oldLease = allocator.grant("eu", 10, now, now.plusSeconds(5));
        allocator.expire("eu", now.plusSeconds(6));
        var newLease = allocator.grant("eu", 10, now.plusSeconds(6), now.plusSeconds(20));

        assertThat(allocator.consume("eu", oldLease.fencingToken(), 1, now.plusSeconds(7))).isFalse();
        assertThat(allocator.consume("eu", newLease.fencingToken(), 1, now.plusSeconds(7))).isTrue();
        assertThat(newLease.fencingToken()).isGreaterThan(oldLease.fencingToken());
    }
}
