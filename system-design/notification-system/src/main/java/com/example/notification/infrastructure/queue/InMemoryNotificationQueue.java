package com.example.notification.infrastructure.queue;

import com.example.notification.application.Ports;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.time.Instant;
import java.time.Clock;
import java.util.Optional;
import java.util.Queue;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.example.notification.domain.DeliveryPriority;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@Profile("!postgres-runtime")
public class InMemoryNotificationQueue implements Ports.NotificationQueue {
    private final Map<DeliveryPriority, Queue<UUID>> queues = new EnumMap<>(DeliveryPriority.class);
    private final AtomicInteger scheduleCursor = new AtomicInteger();
    private static final DeliveryPriority[] WEIGHTED_SCHEDULE = {
            DeliveryPriority.CRITICAL, DeliveryPriority.CRITICAL, DeliveryPriority.CRITICAL, DeliveryPriority.CRITICAL,
            DeliveryPriority.HIGH, DeliveryPriority.HIGH, DeliveryPriority.NORMAL, DeliveryPriority.LOW
    };
    private final Ports.NotificationJobRepository jobRepository;
    private final Clock clock;

    public InMemoryNotificationQueue(Ports.NotificationJobRepository jobRepository, Clock clock) {
        this.jobRepository = jobRepository;
        this.clock = clock;
        for (DeliveryPriority priority : DeliveryPriority.values()) queues.put(priority, new ConcurrentLinkedQueue<>());
    }

    @Override
    public void enqueue(UUID jobId) {
        jobRepository.findById(jobId).ifPresent(job -> queues.get(job.getPriority()).offer(jobId));
    }

    @Override
    public Optional<UUID> pollReadyJob() {
        for (int slot = 0; slot < WEIGHTED_SCHEDULE.length; slot++) {
            DeliveryPriority priority = WEIGHTED_SCHEDULE[Math.floorMod(scheduleCursor.getAndIncrement(), WEIGHTED_SCHEDULE.length)];
            Queue<UUID> queue = queues.get(priority);
            int checked = 0;
            int size = queue.size();
            while (checked++ < size) {
                UUID jobId = queue.poll();
                if (jobId == null) break;
                boolean ready = jobRepository.findById(jobId)
                        .map(job -> !job.getNextAttemptAt().isAfter(clock.instant())).orElse(false);
                if (ready) return Optional.of(jobId);
                queue.offer(jobId);
            }
        }
        return Optional.empty();
    }

    @Override
    public int size() {
        return queues.values().stream().mapToInt(Queue::size).sum();
    }
}
