package com.example.ecommerce.order;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "customer_order_items")
public class CustomerOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private CustomerOrder order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long productVariantId;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String productNameSnapshot;

    @Column(nullable = false)
    private String variantNameSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    protected CustomerOrderItem() {}

    public CustomerOrderItem(
            Long productId,
            Long productVariantId,
            String sku,
            String productNameSnapshot,
            String variantNameSnapshot,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {
        this.productId = productId;
        this.productVariantId = productVariantId;
        this.sku = sku;
        this.productNameSnapshot = productNameSnapshot;
        this.variantNameSnapshot = variantNameSnapshot;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
    }

    void setOrder(CustomerOrder order) {
        this.order = order;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getProductVariantId() { return productVariantId; }
    public String getSku() { return sku; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public String getVariantNameSnapshot() { return variantNameSnapshot; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
