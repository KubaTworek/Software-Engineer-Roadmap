package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

import jakarta.persistence.*;

/**
 * Entity representing order belonging to user.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productName;

    /**
     * Default fetch type for @ManyToOne is EAGER.
     *
     * User is loaded automatically with order.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    protected Order() {
        // Required by JPA
    }

    public Order(String productName, User user) {
        this.productName = productName;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public String getProductName() {
        return productName;
    }

    public User getUser() {
        return user;
    }
}