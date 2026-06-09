package com.example.newsfeed.events;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedKafkaEventRepository extends JpaRepository<ProcessedKafkaEvent, UUID> {
}
