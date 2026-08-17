package com.example.ecommerce.auth;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_users", indexes = {
        @Index(name = "idx_app_users_email", columnList = "email", unique = true)
})
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private String fullName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Set<UserRole> roles = new HashSet<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, String fullName, Set<UserRole> roles) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.roles = roles;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Set<UserRole> getRoles() {
        return roles;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
