package com.example.paymentsystem.payment;

/** Outcome of one interaction with a PSP, including the crucial ambiguous result. */
public enum PaymentAttemptStatus {
    READY,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    UNKNOWN
}
