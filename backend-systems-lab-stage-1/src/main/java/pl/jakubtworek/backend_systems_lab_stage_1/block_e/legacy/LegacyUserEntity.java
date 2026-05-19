package pl.jakubtworek.backend_systems_lab_stage_1.block_e.legacy;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "legacy_users")
public class LegacyUserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String email;
    private LocalDate registeredAt;

    protected LegacyUserEntity() { }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public LocalDate getRegisteredAt() { return registeredAt; }

    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setRegisteredAt(LocalDate registeredAt) { this.registeredAt = registeredAt; }
}
