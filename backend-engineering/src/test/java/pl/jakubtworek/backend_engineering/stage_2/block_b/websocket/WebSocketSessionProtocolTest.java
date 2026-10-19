package pl.jakubtworek.backend_engineering.stage_2.block_b.websocket;

import org.junit.jupiter.api.Test;

import java.util.stream.LongStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSocketSessionProtocolTest {

    @Test
    void reconnectReplaysOnlyEventsAfterLastSeenSequence() {
        ResumableEventLog log = new ResumableEventLog(10);
        log.append("one");
        log.append("two");
        log.append("three");

        assertThat(log.replayAfter(1))
                .extracting(StreamEvent::sequence)
                .containsExactly(2L, 3L);
    }

    @Test
    void reconnectFailsExplicitlyWhenRetentionWindowWasExceeded() {
        ResumableEventLog log = new ResumableEventLog(2);
        log.append("one");
        log.append("two");
        log.append("three");

        assertThatThrownBy(() -> log.replayAfter(0))
                .isInstanceOf(ResumeWindowExceededException.class)
                .hasMessageContaining("replay window 2");
    }

    @Test
    void boundedBufferProtectsProcessFromSlowConsumer() {
        BoundedSessionBuffer buffer = new BoundedSessionBuffer(2);

        assertThat(buffer.offer(new StreamEvent(1, "one"))).isEqualTo(BoundedSessionBuffer.OfferResult.ACCEPTED);
        assertThat(buffer.offer(new StreamEvent(2, "two"))).isEqualTo(BoundedSessionBuffer.OfferResult.ACCEPTED);
        assertThat(buffer.offer(new StreamEvent(3, "three"))).isEqualTo(BoundedSessionBuffer.OfferResult.SLOW_CONSUMER);

        buffer.acknowledgeThrough(1);
        assertThat(buffer.offer(new StreamEvent(3, "three"))).isEqualTo(BoundedSessionBuffer.OfferResult.ACCEPTED);
        assertThat(buffer.pending()).extracting(StreamEvent::sequence).containsExactly(2L, 3L);
    }

    @Test
    void naiveSessionMakesMemoryGrowthProportionalToUnconsumedEvents() {
        NaiveUnboundedSession session = new NaiveUnboundedSession();
        LongStream.rangeClosed(1, 10_000)
                .mapToObj(sequence -> new StreamEvent(sequence, "event"))
                .forEach(session::offer);

        assertThat(session.pendingCount()).isEqualTo(10_000);
    }

    @Test
    void clientIgnoresDuplicateAndRequestsReplayOnGap() {
        ClientEventCursor cursor = new ClientEventCursor();

        assertThat(cursor.apply(new StreamEvent(1, "one"))).isEqualTo(ClientEventCursor.ApplyResult.APPLIED);
        assertThat(cursor.apply(new StreamEvent(1, "duplicate"))).isEqualTo(ClientEventCursor.ApplyResult.DUPLICATE);
        assertThat(cursor.apply(new StreamEvent(3, "gap"))).isEqualTo(ClientEventCursor.ApplyResult.GAP);
        assertThat(cursor.lastApplied()).isEqualTo(1);
    }

    @Test
    void heartbeatUsesMonotonicTimeoutToDetectHalfOpenSession() {
        AtomicLong time = new AtomicLong();
        HeartbeatDeadline heartbeat = new HeartbeatDeadline(Duration.ofSeconds(10), time::get);

        time.addAndGet(Duration.ofSeconds(9).toNanos());
        assertThat(heartbeat.expired()).isFalse();
        time.incrementAndGet();
        heartbeat.signalReceived();
        time.addAndGet(Duration.ofSeconds(10).toNanos());
        assertThat(heartbeat.expired()).isTrue();
    }
}
