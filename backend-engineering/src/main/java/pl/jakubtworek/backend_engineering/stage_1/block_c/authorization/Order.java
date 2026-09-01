package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import jakarta.persistence.*;

/**
 * Example domain entity used for data-based authorization.
 */
@Entity(name = "AuthorizationOrder")
@Table(name = "authorization_orders")
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
        this.ownerUsername = requireNonBlank(ownerUsername, "ownerUsername");
        this.description = requireNonBlank(description, "description");
    }

    public void updateDescription(String description) {
        this.description = requireNonBlank(description, "description");
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

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
