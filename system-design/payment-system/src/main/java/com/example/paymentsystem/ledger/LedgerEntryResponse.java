package com.example.paymentsystem.ledger;

import java.util.UUID;

public record LedgerEntryResponse(UUID entryId, UUID transactionId, String accountId, String direction, long amount,
                                  String currency) {
    public static LedgerEntryResponse from(LedgerEntry e) {
        return new LedgerEntryResponse(e.getEntryId(), e.getTransactionId(), e.getAccountId(), e.getDirection().name(), e.getAmount(), e.getCurrency());
    }
}
