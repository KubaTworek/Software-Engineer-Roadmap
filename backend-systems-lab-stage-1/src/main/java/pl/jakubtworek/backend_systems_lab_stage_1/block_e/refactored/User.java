package pl.jakubtworek.backend_systems_lab_stage_1.block_e.refactored;

import java.time.LocalDate;

public final class User {
    private final Long id;
    private final String username;
    private final String email;
    private final LocalDate registeredAt;

    public User(Long id, String username, String email, LocalDate registeredAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.registeredAt = registeredAt;
    }

    public static User newUser(String username, String email, LocalDate registeredAt) {
        return new User(null, username, email, registeredAt);
    }

    public User withId(Long newId) {
        return new User(newId, username, email, registeredAt);
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public LocalDate getRegisteredAt() { return registeredAt; }
}
