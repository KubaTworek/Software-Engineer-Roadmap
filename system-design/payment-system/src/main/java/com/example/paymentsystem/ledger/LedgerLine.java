package com.example.paymentsystem.ledger;

/** One proposed journal line before it becomes an immutable LedgerEntry. */
public record LedgerLine(String accountId, LedgerDirection direction, long amount, String currency) { }
