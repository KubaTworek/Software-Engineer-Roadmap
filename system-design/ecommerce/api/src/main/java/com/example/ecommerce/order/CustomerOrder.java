package com.example.ecommerce.order;

import com.example.ecommerce.auth.AppUser;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_orders")
public class CustomerOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @ManyToOne(optional = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "PLN";

    @Column(nullable = false, length = 1000)
    private String shippingAddress;

    @Column(nullable = false, length = 1000)
    private String billingAddress;

    @Column(nullable = false)
    private String shippingMethod;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerOrderItem> items = new ArrayList<>();

    protected CustomerOrder() {}

    public CustomerOrder(
            String orderNumber,
            AppUser user,
            BigDecimal subtotalAmount,
            BigDecimal shippingAmount,
            BigDecimal totalAmount,
            String currency,
            String shippingAddress,
            String billingAddress,
            String shippingMethod
    ) {
        this.orderNumber = orderNumber;
        this.user = user;
        this.subtotalAmount = subtotalAmount;
        this.shippingAmount = shippingAmount;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.shippingAddress = shippingAddress;
        this.billingAddress = billingAddress;
        this.shippingMethod = shippingMethod;
    }

    public void addItem(CustomerOrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public AppUser getUser() { return user; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public BigDecimal getShippingAmount() { return shippingAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getShippingAddress() { return shippingAddress; }
    public String getBillingAddress() { return billingAddress; }
    public String getShippingMethod() { return shippingMethod; }
    public Instant getCreatedAt() { return createdAt; }
    public List<CustomerOrderItem> getItems() { return items; }

    public void markPaid() {
        this.status = OrderStatus.PAID;
    }

    public void markPaymentFailed() {
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }
}
