package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private OffsetDateTime startsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Event() {
    }

    public Event(String name, String city, String category, OffsetDateTime startsAt) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.city = city;
        this.category = category;
        this.startsAt = startsAt;
        this.status = EventStatus.PUBLISHED;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getCategory() { return category; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public EventStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
