package com.example.paymentsystem.ledger;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerInvariantTest {

    @Test
    void acceptsOnlyJournalBalancedInEveryCurrency() {
        assertThatCode(() -> LedgerService.requireBalancedPerCurrency(List.of(
                new LedgerLine("cash", LedgerDirection.DEBIT, 100, "PLN"),
                new LedgerLine("merchant", LedgerDirection.CREDIT, 100, "PLN"),
                new LedgerLine("cash", LedgerDirection.DEBIT, 20, "EUR"),
                new LedgerLine("merchant", LedgerDirection.CREDIT, 20, "EUR")
        ))).doesNotThrowAnyException();

        assertThatThrownBy(() -> LedgerService.requireBalancedPerCurrency(List.of(
                new LedgerLine("cash", LedgerDirection.DEBIT, 100, "PLN"),
                new LedgerLine("merchant", LedgerDirection.CREDIT, 100, "EUR")
        ))).isInstanceOf(IllegalStateException.class);
    }
}
