package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * JPA entity mapped to the products table in Cloud SQL.
 *
 * Entity classes represent durable state stored outside application instances.
 */
@Entity
@Table(name = "products", indexes = {@Index(name = "idx_products_name", columnList = "name")})
public class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Product name can be indexed when the application frequently filters by this field. */
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    protected ProductEntity() {
        // Required by JPA.
    }

    public ProductEntity(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }

    public void update(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
    }
}
