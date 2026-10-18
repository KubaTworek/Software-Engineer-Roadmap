package com.example.autocomplete.feedback;

import com.example.autocomplete.privacy.QueryPrivacyService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackStoreTest {
    @Test
    void retryWithSameEventIdIsDeduplicated() {
        var store = new FeedbackStore(new QueryPrivacyService("test-privacy-key-123456789"), Clock.systemUTC());

        assertThat(store.record("event-1", FeedbackType.CLICK, "u1", "s1",
                "john@example.com", List.of("iphone"), "127.0.0.1")).isTrue();
        assertThat(store.record("event-1", FeedbackType.CLICK, "u1", "s1",
                "john@example.com", List.of("iphone"), "127.0.0.1")).isFalse();
        assertThat(store.size()).isEqualTo(1);
    }
}
