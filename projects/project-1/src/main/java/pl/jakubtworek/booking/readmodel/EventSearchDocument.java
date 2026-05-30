package pl.jakubtworek.booking.readmodel;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Document(collection = "event_search_documents")
public class EventSearchDocument {
    @Id
    private UUID eventId;
    private String name;
    private String city;
    private String category;
    private OffsetDateTime startsAt;
    private UUID organizationId;
    private String organizationName;
    private int totalCapacity;
    private int availableCapacity;
    private Map<String, Long> reservationsByStatus;
    private Instant rebuiltAt;

    protected EventSearchDocument() {
    }

    public EventSearchDocument(UUID eventId, String name, String city, String category, OffsetDateTime startsAt,
                               UUID organizationId, String organizationName, int totalCapacity, int availableCapacity,
                               Map<String, Long> reservationsByStatus, Instant rebuiltAt) {
        this.eventId = eventId;
        this.name = name;
        this.city = city;
        this.category = category;
        this.startsAt = startsAt;
        this.organizationId = organizationId;
        this.organizationName = organizationName;
        this.totalCapacity = totalCapacity;
        this.availableCapacity = availableCapacity;
        this.reservationsByStatus = reservationsByStatus;
        this.rebuiltAt = rebuiltAt;
    }

    public UUID getEventId() { return eventId; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getCategory() { return category; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public UUID getOrganizationId() { return organizationId; }
    public String getOrganizationName() { return organizationName; }
    public int getTotalCapacity() { return totalCapacity; }
    public int getAvailableCapacity() { return availableCapacity; }
    public Map<String, Long> getReservationsByStatus() { return reservationsByStatus; }
    public Instant getRebuiltAt() { return rebuiltAt; }
}
