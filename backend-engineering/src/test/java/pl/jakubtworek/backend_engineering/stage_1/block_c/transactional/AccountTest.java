package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    void shouldPreserveDomainInvariantDuringWithdrawalAndDeposit() {
        Account account = new Account(new BigDecimal("100.00"));

        account.withdraw(new BigDecimal("30.00"));
        account.deposit(new BigDecimal("5.50"));

        assertThat(account.getBalance()).isEqualByComparingTo("75.50");
    }

    @Test
    void shouldRejectWithdrawalExceedingBalance() {
        Account account = new Account(new BigDecimal("20.00"));

        assertThatThrownBy(() -> account.withdraw(new BigDecimal("20.01")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("20.00")
                .hasMessageContaining("20.01");
        assertThat(account.getBalance()).isEqualByComparingTo("20.00");
    }

    @Test
    void shouldRejectInvalidMoneyValues() {
        assertThatThrownBy(() -> new Account(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);

        Account account = new Account(BigDecimal.TEN);
        assertThatThrownBy(() -> account.deposit(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.withdraw(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
