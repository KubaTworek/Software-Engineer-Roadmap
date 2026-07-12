package com.example.videostreaming.personalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
public class PersonalizationEventRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PersonalizationEventRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void save(UUID eventId, UUID userId, String eventType, UUID videoId, String sessionId, String source,
                     String deviceType, String country, Map<String, Object> attributes, Instant occurredAt) {
        jdbc.update("""
                insert into personalization_events
                (id, user_id, event_type, video_id, session_id, source, device_type, country, attributes_json, occurred_at, ingested_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                on conflict (id) do nothing
                """,
                eventId == null ? UUID.randomUUID() : eventId,
                userId,
                eventType,
                videoId,
                sessionId,
                source,
                deviceType,
                country,
                toJson(attributes),
                Timestamp.from(occurredAt == null ? Instant.now() : occurredAt),
                Timestamp.from(Instant.now())
        );
    }

    private String toJson(Map<String, Object> attributes) {
        try {
            return objectMapper.writeValueAsString(attributes == null ? Map.of() : attributes);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
