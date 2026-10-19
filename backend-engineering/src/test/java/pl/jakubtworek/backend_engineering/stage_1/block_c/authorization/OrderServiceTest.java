package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    @Test
    void authorizedUseCaseUpdatesAndPersistsTheDomainObject() {
        OrderRepository repository = mock(OrderRepository.class);
        Order order = new Order("alice", "before");
        when(repository.findById(42L)).thenReturn(Optional.of(order));
        when(repository.save(order)).thenReturn(order);

        Order updated = new OrderService(repository).updateOrder(42L, "after");

        assertThat(updated.getDescription()).isEqualTo("after");
        verify(repository).save(order);
    }

    @Test
    void invalidIdentifierFailsBeforeRepositoryAccess() {
        OrderRepository repository = mock(OrderRepository.class);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OrderService(repository).getOrder(0L));

        verify(repository, never()).findById(0L);
    }
}
