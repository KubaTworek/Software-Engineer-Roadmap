package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Customer() {
    }

    public Customer(String fullName, String email) {
        this.id = UUID.randomUUID();
        this.fullName = fullName;
        this.email = email;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public Instant getCreatedAt() { return createdAt; }
}
