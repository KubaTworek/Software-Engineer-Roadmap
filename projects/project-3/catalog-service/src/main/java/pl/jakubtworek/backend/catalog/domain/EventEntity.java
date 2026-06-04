package pl.jakubtworek.backend.catalog.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
public class EventEntity {
    @Id
    private UUID id;
    private String name;
    private String venue;
    private Instant startsAt;
    private int totalTickets;
    private int availableTickets;

    protected EventEntity() {
    }

    public EventEntity(UUID id, String name, String venue, Instant startsAt, int totalTickets, int availableTickets) {
        this.id = id;
        this.name = name;
        this.venue = venue;
        this.startsAt = startsAt;
        this.totalTickets = totalTickets;
        this.availableTickets = availableTickets;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getVenue() { return venue; }
    public Instant getStartsAt() { return startsAt; }
    public int getTotalTickets() { return totalTickets; }
    public int getAvailableTickets() { return availableTickets; }
}
