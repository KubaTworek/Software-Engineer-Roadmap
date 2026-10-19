package com.example.paymentsystem.approval;

import com.example.paymentsystem.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Maker-checker workflow. Approval is single-use and bound to operation,
 * target, amount and currency, so it cannot become a reusable privilege token.
 */
@Service
public class ApprovalService {
    private final ApprovalRequestRepository repository;
    private final AuditService audit;
    private final long refundThreshold;
    private final long payoutThreshold;

    public ApprovalService(ApprovalRequestRepository repository, AuditService audit,
                           @Value("${payment-system.approval.refund-threshold:100000}") long refundThreshold,
                           @Value("${payment-system.approval.payout-threshold:500000}") long payoutThreshold) {
        this.repository = repository;
        this.audit = audit;
        this.refundThreshold = refundThreshold;
        this.payoutThreshold = payoutThreshold;
    }

    @Transactional
    public ApprovalRequest request(ApprovalOperation operation, String targetId, long amount, String currency,
                                   String requester) {
        ApprovalRequest approval = repository.save(new ApprovalRequest(operation, targetId, amount, currency, requester));
        audit.record(requester, "APPROVAL_REQUESTED", "APPROVAL", approval.getApprovalId().toString(),
                operation + ",target=" + targetId + ",amount=" + amount + ",currency=" + currency);
        return approval;
    }

    @Transactional
    public ApprovalRequest approve(UUID approvalId, String approver) {
        ApprovalRequest approval = repository.findById(approvalId).orElseThrow();
        approval.approve(approver);
        audit.record(approver, "APPROVAL_GRANTED", "APPROVAL", approvalId.toString(), null);
        return approval;
    }

    public boolean refundNeedsApproval(long amount) { return amount >= refundThreshold; }
    public boolean payoutNeedsApproval(long amount) { return amount >= payoutThreshold; }

    @Transactional
    public void requireAndConsume(ApprovalOperation operation, String targetId, long amount, String currency,
                                  UUID approvalId) {
        if (approvalId == null) throw new IllegalStateException("Approved maker-checker request is required");
        ApprovalRequest approval = repository.findById(approvalId).orElseThrow();
        approval.consume(operation, targetId, amount, currency);
        audit.record("system", "APPROVAL_CONSUMED", "APPROVAL", approvalId.toString(), operation.name());
    }
}
