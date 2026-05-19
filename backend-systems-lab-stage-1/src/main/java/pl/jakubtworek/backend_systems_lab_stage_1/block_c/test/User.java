package pl.jakubtworek.backend_systems_lab_stage_1.block_c.test;

import jakarta.persistence.*;

/**
 * Simple JPA entity used in tests.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    protected User() {
        // Required by JPA
    }

    public User(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}