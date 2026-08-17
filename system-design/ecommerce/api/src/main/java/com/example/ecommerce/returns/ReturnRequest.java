package com.example.ecommerce.returns;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.order.CustomerOrder;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "return_requests")
public class ReturnRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private CustomerOrder order;

    @ManyToOne(optional = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnStatus status = ReturnStatus.REQUESTED;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedRefundAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReturnRequestItem> items = new ArrayList<>();

    protected ReturnRequest() {}

    public ReturnRequest(CustomerOrder order, AppUser user, String reason) {
        this.order = order;
        this.user = user;
        this.reason = reason;
    }

    public void addItem(ReturnRequestItem item) {
        items.add(item);
        item.setReturnRequest(this);
        requestedRefundAmount = requestedRefundAmount.add(item.getLineRefundAmount());
    }

    public Long getId() { return id; }
    public CustomerOrder getOrder() { return order; }
    public AppUser getUser() { return user; }
    public ReturnStatus getStatus() { return status; }
    public BigDecimal getRequestedRefundAmount() { return requestedRefundAmount; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ReturnRequestItem> getItems() { return items; }

    public void approve() { this.status = ReturnStatus.APPROVED; }
    public void reject() { this.status = ReturnStatus.REJECTED; }
    public void markReceived() { this.status = ReturnStatus.RECEIVED; }
    public void markRefunded() { this.status = ReturnStatus.REFUNDED; }
}
