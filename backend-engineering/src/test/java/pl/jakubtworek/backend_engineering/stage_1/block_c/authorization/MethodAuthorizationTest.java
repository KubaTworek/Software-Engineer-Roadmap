package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringJUnitConfig({MethodSecurityConfig.class, MethodAuthorizationTest.TestConfig.class})
class MethodAuthorizationTest {

    @Configuration
    static class TestConfig {
        @Bean
        OrderRepository orderRepository() {
            return mock(OrderRepository.class);
        }

        @Bean(name = "userSecurity")
        UserSecurity userSecurity(OrderRepository repository) {
            return new UserSecurity(repository);
        }

        @Bean
        OrderService orderService(OrderRepository repository) {
            return new OrderService(repository);
        }
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository repository;

    @BeforeEach
    void resetRepository() {
        reset(repository);
    }

    @Test
    @WithMockUser(authorities = "ORDER_READ")
    void shouldAllowTheRequiredAuthorityThroughTheRealSecurityProxy() {
        Order order = new Order("alice", "description");
        when(repository.findById(1L)).thenReturn(Optional.of(order));

        assertThat(orderService.getOrder(1L)).isSameAs(order);
    }

    @Test
    @WithMockUser(authorities = "OTHER_PERMISSION")
    void shouldDenyBeforeCallingTheRepository() {
        assertThatThrownBy(() -> orderService.getOrder(1L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @WithMockUser(username = "bob")
    void shouldDenyUpdatingAnotherUsersOrder() {
        when(repository.findById(1L)).thenReturn(Optional.of(new Order("alice", "before")));

        assertThatThrownBy(() -> orderService.updateOrder(1L, "changed"))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(any());
    }
}
