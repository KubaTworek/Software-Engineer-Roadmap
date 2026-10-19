package com.example.notification;

import com.example.notification.outbox.OutboxEvent;
import com.example.notification.outbox.OutboxEventRepository;
import com.example.notification.outbox.ProcessedNotificationEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class NotificationWorkerTest {
    @Test
    void outboxIdBecomesStableProviderIdempotencyKey() {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        ProcessedNotificationEventRepository processed = mock(ProcessedNotificationEventRepository.class);
        EmailClient email = mock(EmailClient.class);
        OutboxEvent event = mock(OutboxEvent.class);
        when(event.getId()).thenReturn(42L);
        when(event.getEventType()).thenReturn("PaymentSucceeded");
        when(event.getPayloadJson()).thenReturn("{}");
        when(outbox.findUnprocessedNotificationEvents()).thenReturn(List.of(event));

        new NotificationWorker(outbox, processed, email, 10, new SimpleMeterRegistry()).process();

        verify(email).sendTransactionalEmail("outbox-42", "payment-succeeded", "{}");
        verify(processed).save(any());
    }
}
