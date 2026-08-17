package com.example.notification.infrastructure.queue;

import com.example.notification.application.Ports;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class InMemoryNotificationQueue implements Ports.NotificationQueue {
    private final Queue<UUID> queue = new ConcurrentLinkedQueue<>();
    private final Ports.NotificationJobRepository jobRepository;

    public InMemoryNotificationQueue(Ports.NotificationJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public void enqueue(UUID jobId) {
        queue.offer(jobId);
    }

    @Override
    public Optional<UUID> pollReadyJob() {
        int checked = 0;
        int size = queue.size();
        while (checked < size) {
            UUID jobId = queue.poll();
            if (jobId == null) return Optional.empty();
            boolean ready = jobRepository.findById(jobId).map(job -> !job.getNextAttemptAt().isAfter(Instant.now())).orElse(false);
            if (ready) return Optional.of(jobId);
            queue.offer(jobId);
            checked++;
        }
        return Optional.empty();
    }

    @Override
    public int size() {
        return queue.size();
    }
}
