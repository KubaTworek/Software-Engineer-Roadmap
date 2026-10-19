package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

import jakarta.persistence.*;

/**
 * Entity representing order belonging to user.
 */
@Entity(name = "JpaExampleOrder")
@Table(
        name = "jpa_orders",
        indexes = @Index(name = "idx_jpa_orders_user_id", columnList = "user_id")
)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    /**
     * ManyToOne defaults to EAGER, so the lab overrides it explicitly. Fetch
     * plans should follow a use case, not an annotation default.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected Order() {
        // Required by JPA
    }

    public Order(String productName, User user) {
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("productName must not be blank");
        }
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
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
