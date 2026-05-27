package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant confirmedAt;

    @Column
    private Instant cancelledAt;

    protected Reservation() {
    }

    public Reservation(Event event, Customer customer) {
        this.id = UUID.randomUUID();
        this.event = event;
        this.customer = customer;
        this.status = ReservationStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void confirm() {
        if (status != ReservationStatus.PENDING) {
            throw new IllegalStateException("Only pending reservation can be confirmed");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
    }

    public boolean cancel() {
        if (status == ReservationStatus.CANCELLED) {
            return false;
        }
        if (status == ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Confirmed reservation cannot be cancelled in the base version");
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        return true;
    }

    public UUID getId() { return id; }
    public Event getEvent() { return event; }
    public Customer getCustomer() { return customer; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
