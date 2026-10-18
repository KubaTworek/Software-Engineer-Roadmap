package pl.jakubtworek.chatsystem.realtime.flow;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RealtimeFlowControlTest {
    @Test
    void coalescesEphemeralStateButNeverSilentlyDropsDurableMessage() {
        BoundedRealtimeBuffer buffer = new BoundedRealtimeBuffer(2);
        assertThat(buffer.offer(new BoundedRealtimeBuffer.Frame("typing:alice", "on",
                BoundedRealtimeBuffer.Priority.EPHEMERAL))).isEqualTo(BoundedRealtimeBuffer.Result.ACCEPTED);
        assertThat(buffer.offer(new BoundedRealtimeBuffer.Frame("typing:alice", "off",
                BoundedRealtimeBuffer.Priority.EPHEMERAL))).isEqualTo(BoundedRealtimeBuffer.Result.COALESCED);
        buffer.offer(new BoundedRealtimeBuffer.Frame("message:1", "hello", BoundedRealtimeBuffer.Priority.DURABLE));

        assertThat(buffer.offer(new BoundedRealtimeBuffer.Frame("message:2", "world",
                BoundedRealtimeBuffer.Priority.DURABLE)))
                .isEqualTo(BoundedRealtimeBuffer.Result.RECONNECT_REQUIRED);
    }

    @Test
    void reconnectCatchUpIsBoundedAndRetryUsesBackoffWithJitter() {
        ReconnectAdmissionController controller = new ReconnectAdmissionController(1);
        try (var lease = controller.tryAcquire().orElseThrow()) {
            assertThat(controller.tryAcquire()).isEmpty();
        }
        assertThat(controller.tryAcquire()).isPresent();
        assertThat(controller.recommendedRetryDelay(4, 17).toMillis()).isBetween(800L, 1599L);
    }
}
