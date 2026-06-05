package com.example.newsfeed.user;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 500)
    private String bio;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(
            UUID id,
            String email,
            String username,
            String displayName,
            String passwordHash,
            String bio,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.bio = bio;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getBio() {
        return bio;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void updateProfile(String displayName, String bio) {
        if (displayName != null && !displayName.isBlank()) {
            this.displayName = displayName.trim();
        }

        if (bio != null) {
            String trimmedBio = bio.trim();
            this.bio = trimmedBio.isBlank() ? null : trimmedBio;
        }

        this.updatedAt = Instant.now();
    }
}
