package com.example.newsfeed.auth;

import com.example.newsfeed.user.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_tokens")
public class AuthToken {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    protected AuthToken() {
    }

    public AuthToken(UUID id, String token, User user, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.token = token;
        this.user = user;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public User getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }
}
