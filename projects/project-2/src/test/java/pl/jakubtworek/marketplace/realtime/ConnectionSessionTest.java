package pl.jakubtworek.marketplace.realtime;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectionSessionTest {
    @Test void boundsSlowClientQueueAndRejectsExpiredAuthorization() {
        Instant expiry = Instant.parse("2030-01-01T00:00:00Z");
        var session = new ConnectionSession(2, expiry);
        session.enqueue("v1", expiry.minusSeconds(2));
        session.enqueue("v2", expiry.minusSeconds(1));
        session.enqueue("v3", expiry.minusMillis(1));
        assertThat(session.poll()).contains("v2");
        assertThat(session.droppedMessages()).isOne();
        assertThatThrownBy(() -> session.enqueue("v4", expiry)).isInstanceOf(SecurityException.class);
    }
}
