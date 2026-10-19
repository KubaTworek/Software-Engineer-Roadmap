package pl.jakubtworek.backend.reservation.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

/**
 * Autorytatywna pula biletów należąca do Reservation Service.
 *
 * Catalog Service może prezentować opóźniony read model dostępności, ale decyzja o
 * przydziale musi zostać podjęta atomowo w bazie właściciela tego niezmiennika.
 */
@Entity
@Table(name = "event_inventory")
public class EventInventoryEntity {
    @Id
    private UUID eventId;
    private int totalTickets;
    private int availableTickets;
    @Version
    private long version;

    protected EventInventoryEntity() {
    }

    public UUID getEventId() { return eventId; }
    public int getTotalTickets() { return totalTickets; }
    public int getAvailableTickets() { return availableTickets; }
    public long getVersion() { return version; }
}
