package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "capacity_pools")
public class CapacityPool {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    @Column(nullable = false)
    private int totalCapacity;

    @Column(nullable = false)
    private int availableCapacity;

    @Version
    private long version;

    protected CapacityPool() {
    }

    public CapacityPool(Event event, int totalCapacity) {
        if (totalCapacity <= 0) {
            throw new IllegalArgumentException("totalCapacity must be positive");
        }
        this.id = UUID.randomUUID();
        this.event = event;
        this.totalCapacity = totalCapacity;
        this.availableCapacity = totalCapacity;
    }

    public void reserveOne() {
        if (availableCapacity <= 0) {
            throw new IllegalStateException("No available capacity");
        }
        this.availableCapacity--;
    }

    public void releaseOne() {
        if (availableCapacity >= totalCapacity) {
            throw new IllegalStateException("Capacity cannot exceed total capacity");
        }
        this.availableCapacity++;
    }

    public UUID getId() { return id; }
    public Event getEvent() { return event; }
    public int getTotalCapacity() { return totalCapacity; }
    public int getAvailableCapacity() { return availableCapacity; }
    public long getVersion() { return version; }
}
