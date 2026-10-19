package com.example.paymentsystem.approval;

import com.example.paymentsystem.audit.AuditService;
import com.example.paymentsystem.ledger.LedgerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Production boundary: every manual journal requires an exact, single-use approval. */
@Service
public class ManualLedgerCorrectionService {
    private final ApprovalService approvals;
    private final LedgerService ledger;
    private final AuditService audit;

    public ManualLedgerCorrectionService(ApprovalService approvals, LedgerService ledger, AuditService audit) {
        this.approvals = approvals;
        this.ledger = ledger;
        this.audit = audit;
    }

    @Transactional
    public void post(ManualLedgerCorrectionRequest request, UUID approvalId, String actor) {
        approvals.requireAndConsume(ApprovalOperation.LEDGER_CORRECTION, request.correctionId().toString(),
                request.amount(), request.currency(), approvalId);
        ledger.recordManualCorrection(request.correctionId(), request.debitAccount(), request.creditAccount(),
                request.amount(), request.currency());
        audit.record(actor, "MANUAL_LEDGER_CORRECTION", "LEDGER_CORRECTION",
                request.correctionId().toString(), request.debitAccount() + "->" + request.creditAccount());
    }
}
