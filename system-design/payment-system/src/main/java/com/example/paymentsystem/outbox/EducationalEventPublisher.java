package com.example.paymentsystem.outbox;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory broker substitute; it demonstrates the boundary without claiming Kafka durability. */
@Component
public class EducationalEventPublisher implements EventPublisher {
    private final List<String> publishedEventTypes = new CopyOnWriteArrayList<>();

    @Override
    public void publish(OutboxEvent event) {
        publishedEventTypes.add(event.getEventType());
    }

    public List<String> publishedEventTypes() {
        return List.copyOf(publishedEventTypes);
    }
}
