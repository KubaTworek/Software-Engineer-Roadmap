package com.example.paymentsystem.approval;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ApprovalRequestTest {
    @Test
    void requesterCannotApproveOwnOperation() {
        ApprovalRequest approval = new ApprovalRequest(ApprovalOperation.HIGH_VALUE_REFUND,
                "payment-1", 100_000, "PLN", "alice");
        assertThatThrownBy(() -> approval.approve("alice"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approvalIsBoundToCommandAndCanBeConsumedOnlyOnce() {
        ApprovalRequest approval = new ApprovalRequest(ApprovalOperation.HIGH_VALUE_REFUND,
                "payment-1", 100_000, "PLN", "alice");
        approval.approve("bob");

        assertThatThrownBy(() -> approval.consume(ApprovalOperation.HIGH_VALUE_REFUND,
                "payment-1", 100_001, "PLN")).isInstanceOf(IllegalArgumentException.class);
        approval.consume(ApprovalOperation.HIGH_VALUE_REFUND, "payment-1", 100_000, "PLN");
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.CONSUMED);
        assertThatThrownBy(() -> approval.consume(ApprovalOperation.HIGH_VALUE_REFUND,
                "payment-1", 100_000, "PLN")).isInstanceOf(IllegalStateException.class);
    }
}
