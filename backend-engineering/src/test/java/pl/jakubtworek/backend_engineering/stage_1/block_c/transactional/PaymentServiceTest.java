package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PaymentService service = new PaymentService(accountRepository, auditService);

    @Test
    void shouldAcquirePessimisticLocksInStableIdOrder() {
        Account lowerIdAccount = new Account(new BigDecimal("20.00"));
        Account higherIdAccount = new Account(new BigDecimal("100.00"));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(lowerIdAccount));
        when(accountRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(higherIdAccount));

        TransferResult result = service.transferPessimistically(
                9L,
                2L,
                new BigDecimal("30.00")
        );

        InOrder lockOrder = inOrder(accountRepository);
        lockOrder.verify(accountRepository).findByIdForUpdate(2L);
        lockOrder.verify(accountRepository).findByIdForUpdate(9L);
        assertThat(result.fromBalance()).isEqualByComparingTo("70.00");
        assertThat(result.toBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldRejectInvalidCommandBeforeAccessingDatabase() {
        assertThatThrownBy(() -> service.transferOptimistically(1L, 1L, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must differ");

        verifyNoInteractions(accountRepository, auditService);
    }
}
