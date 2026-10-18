package com.example.urlshortener.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.storage.simulation.VersionedUrlLookupStore;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VersionedUrlLookupStoreTest {
    @Test
    void staleActiveDeliveryCannotResurrectBlockedLink() {
        var store = new VersionedUrlLookupStore();
        Instant now = Instant.parse("2026-09-14T00:00:00Z");
        var activeV1 = new UrlLookupRecord("promo", "https://example.com", UrlStatus.ACTIVE, null, "eu", now, 1);
        var blockedV2 = new UrlLookupRecord("promo", "https://example.com", UrlStatus.BLOCKED, null, "eu", now.plusSeconds(1), 2);

        store.apply(blockedV2);
        store.apply(activeV1); // delayed delivery from before moderation

        assertThat(store.find("promo")).contains(blockedV2);
    }

    @Test
    void duplicateDeliveryIsIdempotent() {
        var store = new VersionedUrlLookupStore();
        var record = new UrlLookupRecord("a", "https://example.com", UrlStatus.DELETED, null, "eu", Instant.EPOCH, 4);
        store.apply(record);
        store.apply(record);
        assertThat(store.find("a")).contains(record);
    }
}
