package pl.jakubtworek.marketplace.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import pl.jakubtworek.marketplace.ordering.domain.CustomerId;
import pl.jakubtworek.marketplace.ordering.domain.Order;
import pl.jakubtworek.marketplace.ordering.domain.OrderLine;
import pl.jakubtworek.marketplace.ordering.infrastructure.InMemoryOrderRepository;
import pl.jakubtworek.marketplace.shared.kernel.Money;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class OrderOwnershipTest {
    @Test void preventsIdorEvenWhenCallerKnowsAnotherOrderId() {
        UUID owner = UUID.randomUUID(); UUID attacker = UUID.randomUUID();
        var orders = new InMemoryOrderRepository();
        var order = Order.place(CustomerId.of(owner), List.of(new OrderLine(UUID.randomUUID(), 1, Money.of("1", "PLN"))), UUID.randomUUID());
        orders.save(order);
        var policy = new OrderOwnership(orders);

        assertThat(policy.canRead(order.id().value(), auth(owner))).isTrue();
        assertThat(policy.canRead(order.id().value(), auth(attacker))).isFalse();
        assertThat(policy.canRead(UUID.randomUUID(), auth(owner))).isFalse(); // fail closed
    }
    private JwtAuthenticationToken auth(UUID customerId) {
        Jwt jwt = new Jwt("redacted", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", "user", "customer_id", customerId.toString()));
        return new JwtAuthenticationToken(jwt);
    }
}
