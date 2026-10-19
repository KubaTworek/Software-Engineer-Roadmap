package com.example.newsfeed.events;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProcessedKafkaEventRepository extends JpaRepository<ProcessedKafkaEvent, ProcessedKafkaEventId> {

    long deleteByProcessedAtBefore(java.time.Instant cutoff);

    @Modifying
    @Query(value = """
            INSERT INTO processed_kafka_events(consumer_name, event_id, event_type, processed_at)
            VALUES (:consumerName, :eventId, :eventType, now())
            ON CONFLICT (consumer_name, event_id) DO NOTHING
            """, nativeQuery = true)
    int claim(String consumerName, java.util.UUID eventId, String eventType);
}
