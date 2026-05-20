package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import jakarta.persistence.*;

/**
 * Example domain entity used for data-based authorization.
 */
@Entity
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ownerUsername;

    private String description;

    protected Order() {
        // Required by JPA
    }

    public Order(String ownerUsername, String description) {
        this.ownerUsername = ownerUsername;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public String getDescription() {
        return description;
    }
}