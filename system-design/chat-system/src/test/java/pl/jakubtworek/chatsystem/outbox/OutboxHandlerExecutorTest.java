package pl.jakubtworek.chatsystem.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OutboxHandlerExecutorTest {
    @Autowired OutboxService outbox;
    @Autowired OutboxHandlerExecutor executor;

    @Test
    void handlerInboxMakesRedeliveryIdempotentPerConsumer() {
        OutboxEvent event = outbox.append(UUID.randomUUID(), "education.test", java.util.Map.of("value", 1));
        AtomicInteger effects = new AtomicInteger();
        DomainEventHandler handler = new DomainEventHandler() {
            @Override public boolean supports(String eventType) { return true; }
            @Override public void handle(OutboxEvent ignored) { effects.incrementAndGet(); }
        };

        executor.executeOnce(event, handler);
        executor.executeOnce(event, handler);

        assertThat(effects).hasValue(1);
    }
}
