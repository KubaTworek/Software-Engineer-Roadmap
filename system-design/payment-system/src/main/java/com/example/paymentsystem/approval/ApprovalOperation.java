package com.example.paymentsystem.approval;

/** Operations whose blast radius justifies separation of duties. */
public enum ApprovalOperation {
    HIGH_VALUE_REFUND,
    HIGH_VALUE_PAYOUT,
    LEDGER_CORRECTION
}
