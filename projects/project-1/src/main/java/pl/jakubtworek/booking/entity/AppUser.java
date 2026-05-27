package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(Organization organization, String email, UserRole role) {
        this.id = UUID.randomUUID();
        this.organization = organization;
        this.email = email;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Organization getOrganization() { return organization; }
    public String getEmail() { return email; }
    public UserRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
