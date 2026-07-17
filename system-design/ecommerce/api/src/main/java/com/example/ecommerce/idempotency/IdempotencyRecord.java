package com.example.ecommerce.idempotency;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "idempotency_records", indexes = @Index(name = "idx_idempotency_key_user_operation", columnList = "idempotencyKey,userId,operation", unique = true))
public class IdempotencyRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String idempotencyKey;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private String operation;
    @Column(nullable = false, length = 128) private String requestHash;
    private Long orderId; private Long paymentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private IdempotencyStatus status = IdempotencyStatus.PROCESSING;
    @Column(nullable = false) private Instant createdAt = Instant.now(); private Instant completedAt;
    protected IdempotencyRecord() {}
    public IdempotencyRecord(String idempotencyKey, Long userId, String operation, String requestHash){ this.idempotencyKey=idempotencyKey; this.userId=userId; this.operation=operation; this.requestHash=requestHash; }
    public String getRequestHash(){ return requestHash; } public Long getOrderId(){ return orderId; } public Long getPaymentId(){ return paymentId; } public IdempotencyStatus getStatus(){ return status; }
    public void complete(Long orderId, Long paymentId){ this.orderId=orderId; this.paymentId=paymentId; this.status=IdempotencyStatus.COMPLETED; this.completedAt=Instant.now(); }
    public void fail(){ this.status=IdempotencyStatus.FAILED; }
}
