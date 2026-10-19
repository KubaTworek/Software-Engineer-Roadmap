package com.example.videostreaming.transcoding;

import com.example.videostreaming.messaging.EventPublisher;
import com.example.videostreaming.messaging.VideoEvents;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Outbox zamyka okno „commit w PostgreSQL się udał, publikacja do RabbitMQ nie”.
 * Relay jest at-least-once, dlatego consumer nadal musi być idempotentny.
 */
@Service
public class TranscodingOutboxService {
    private final TranscodingOutboxRepository messages;
    private final EventPublisher publisher;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public TranscodingOutboxService(TranscodingOutboxRepository messages, EventPublisher publisher, Clock clock,
                                    TransactionTemplate transactions) {
        this.messages = messages;
        this.publisher = publisher;
        this.clock = clock;
        this.transactions = transactions;
    }

    public void enqueue(TranscodingJob job) {
        messages.save(new TranscodingOutboxMessage(
                job.getId(), job.getVideo().getId(), job.getSourceObjectKey(), 1, clock.instant()));
    }

    @Scheduled(fixedDelayString = "${app.transcoding.outbox-poll-ms:1000}")
    public void relay() {
        for (TranscodingOutboxMessage message : messages.findByPublishedAtIsNullOrderByCreatedAt(PageRequest.of(0, 50))) {
            transactions.executeWithoutResult(ignored -> deliver(message.getId()));
        }
    }

    private void deliver(java.util.UUID id) {
        TranscodingOutboxMessage message = messages.findById(id).orElseThrow();
        if (message.getPublishedAt() != null) return;
        try {
            publisher.publishTranscodingRequested(new VideoEvents.TranscodingRequested(
                    message.getJobId(), message.getVideoId(), message.getSourceObjectKey(),
                    message.getAttempt(), message.getCreatedAt()));
            message.published(clock.instant());
        } catch (RuntimeException exception) {
            // Nie propagujemy błędu poza krótką transakcję. Dzięki temu licznik
            // prób zostaje zapisany, a jeden niedostępny komunikat nie blokuje
            // pozostałych rekordów w tej samej paczce relaya.
            message.failed(exception.getMessage());
        }
    }
}
