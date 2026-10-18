package com.example.paymentsystem.operation;

/** Rodzaj efektu finansowego wykonywanego poza lokalną transakcją bazy. */
public enum ProviderOperationType {
    CREATE_PAYMENT,
    AUTHORIZE,
    CAPTURE,
    REFUND,
    CANCEL,
    PAYOUT
}
