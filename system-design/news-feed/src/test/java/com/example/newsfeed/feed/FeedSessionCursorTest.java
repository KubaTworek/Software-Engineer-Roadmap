package com.example.newsfeed.feed;

import com.example.newsfeed.common.ConflictException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class FeedSessionCursorTest {
    @Test
    void cursorRoundTripPreservesSnapshotAndOffset() {
        FeedSessionCursor original = new FeedSessionCursor(UUID.randomUUID(), 40);
        assertThat(FeedSessionCursor.decode(original.encode())).contains(original);
    }

    @Test
    void malformedCursorFailsExplicitlyInsteadOfRestartingPagination() {
        assertThatThrownBy(() -> FeedSessionCursor.decode("not-a-cursor"))
                .isInstanceOf(ConflictException.class);
    }
}
