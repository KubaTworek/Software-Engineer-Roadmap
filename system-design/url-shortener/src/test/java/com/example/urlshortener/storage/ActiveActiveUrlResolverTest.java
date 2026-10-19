package com.example.urlshortener.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.storage.simulation.ActiveActiveUrlResolver;
import com.example.urlshortener.storage.simulation.ActiveActiveUrlResolver.VersionedValue;
import org.junit.jupiter.api.Test;

class ActiveActiveUrlResolverTest {
    private final ActiveActiveUrlResolver resolver = new ActiveActiveUrlResolver();

    @Test
    void safetyTombstoneWinsOverConcurrentActiveWrite() {
        var active = new VersionedValue("code", "https://new.example", UrlStatus.ACTIVE, 9, "eu");
        var blocked = new VersionedValue("code", "https://old.example", UrlStatus.BLOCKED, 9, "us");
        assertThat(resolver.resolve(active, blocked).status()).isEqualTo(UrlStatus.BLOCKED);
    }

    @Test
    void higherLogicalRevisionWinsWithoutDependingOnWallClock() {
        var older = new VersionedValue("code", "https://old.example", UrlStatus.ACTIVE, 10, "eu");
        var newer = new VersionedValue("code", "https://new.example", UrlStatus.ACTIVE, 11, "us");
        assertThat(resolver.resolve(older, newer).longUrl()).isEqualTo("https://new.example");
    }
}
