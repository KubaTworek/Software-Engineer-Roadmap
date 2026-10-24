package pl.jakubtworek.marketplace.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.ordering.application.OrderRepository;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;
import java.util.UUID;

/** Jedna polityka własności używana przez REST i GraphQL; brak danych oznacza deny. */
@Component("orderOwnership")
public class OrderOwnership {
    private final OrderRepository orders;
    public OrderOwnership(OrderRepository orders) { this.orders = orders; }
    public boolean canCreate(UUID customerId, Authentication authentication) {
        return admin(authentication) || customer(authentication).map(customerId::equals).orElse(false);
    }
    public boolean canRead(UUID orderId, Authentication authentication) {
        if (admin(authentication)) return true;
        return orders.findById(OrderId.of(orderId))
                .flatMap(order -> customer(authentication).map(order.customerId().value()::equals))
                .orElse(false);
    }
    private boolean admin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_admin"));
    }
    private java.util.Optional<UUID> customer(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) return java.util.Optional.empty();
        try { return java.util.Optional.of(UUID.fromString(jwt.hasClaim("customer_id") ? jwt.getClaimAsString("customer_id") : jwt.getSubject())); }
        catch (RuntimeException missingOrInvalid) { return java.util.Optional.empty(); }
    }
}
