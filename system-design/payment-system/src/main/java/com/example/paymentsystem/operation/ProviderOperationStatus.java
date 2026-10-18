package com.example.paymentsystem.operation;

/**
 * Status trwałej próby wykonania efektu zewnętrznego. UNKNOWN jest stanem
 * biznesowym: request mógł dotrzeć do providera, dlatego ślepy retry jest
 * niebezpieczny i wymaga odczytu stanu albo reconciliation.
 */
public enum ProviderOperationStatus {
    READY,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    UNKNOWN
}
