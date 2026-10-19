package com.example.paymentsystem.approval;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable binding between an approval and the exact financial command. An
 * approval for 100 PLN must never authorize 1,000 PLN or another payment.
 */
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest {
    @Id @Column(name = "approval_id") private UUID approvalId;
    @Enumerated(EnumType.STRING) @Column(name = "operation", nullable = false) private ApprovalOperation operation;
    @Column(name = "target_id", nullable = false) private String targetId;
    @Column(name = "amount", nullable = false) private long amount;
    @Column(name = "currency", nullable = false) private String currency;
    @Column(name = "requested_by", nullable = false) private String requestedBy;
    @Column(name = "approved_by") private String approvedBy;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private ApprovalStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Version @Column(name = "version", nullable = false) private long version;

    protected ApprovalRequest() { }

    public ApprovalRequest(ApprovalOperation operation, String targetId, long amount, String currency,
                           String requestedBy) {
        if (amount <= 0) throw new IllegalArgumentException("Approval amount must be positive");
        this.approvalId = UUID.randomUUID();
        this.operation = operation;
        this.targetId = targetId;
        this.amount = amount;
        this.currency = currency;
        this.requestedBy = requestedBy;
        this.status = ApprovalStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void approve(String approver) {
        if (status != ApprovalStatus.PENDING) throw new IllegalStateException("Approval is not pending");
        if (requestedBy.equals(approver)) {
            throw new IllegalArgumentException("Requester cannot approve their own financial operation");
        }
        approvedBy = approver;
        approvedAt = Instant.now();
        status = ApprovalStatus.APPROVED;
    }

    public void consume(ApprovalOperation expectedOperation, String expectedTarget, long expectedAmount,
                        String expectedCurrency) {
        if (status != ApprovalStatus.APPROVED) throw new IllegalStateException("Approval is not approved");
        if (operation != expectedOperation || !targetId.equals(expectedTarget)
                || amount != expectedAmount || !currency.equals(expectedCurrency)) {
            throw new IllegalArgumentException("Approval does not match the financial command");
        }
        status = ApprovalStatus.CONSUMED;
        consumedAt = Instant.now();
    }

    public UUID getApprovalId() { return approvalId; }
    public ApprovalOperation getOperation() { return operation; }
    public String getTargetId() { return targetId; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getRequestedBy() { return requestedBy; }
    public String getApprovedBy() { return approvedBy; }
    public ApprovalStatus getStatus() { return status; }
}
