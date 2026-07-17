package com.example.ecommerce.returns;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "return_request_items")
public class ReturnRequestItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private ReturnRequest returnRequest;

    @Column(nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal lineRefundAmount;

    protected ReturnRequestItem() {}

    public ReturnRequestItem(Long orderItemId, int quantity, BigDecimal lineRefundAmount) {
        this.orderItemId = orderItemId;
        this.quantity = quantity;
        this.lineRefundAmount = lineRefundAmount;
    }

    void setReturnRequest(ReturnRequest returnRequest) {
        this.returnRequest = returnRequest;
    }

    public Long getId() { return id; }
    public Long getOrderItemId() { return orderItemId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineRefundAmount() { return lineRefundAmount; }
}
