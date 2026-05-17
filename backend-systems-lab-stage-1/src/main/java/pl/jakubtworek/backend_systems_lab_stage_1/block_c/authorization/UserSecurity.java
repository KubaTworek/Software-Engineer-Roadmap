package pl.jakubtworek.backend_systems_lab_stage_1.block_c.authorization;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Custom authorization component used from SpEL.
 *
 * This keeps complex authorization rules outside annotations,
 * making code cleaner and easier to test.
 */
@Component("userSecurity")
public class UserSecurity {

    private final OrderRepository orderRepository;

    public UserSecurity(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Checks whether authenticated user owns the order.
     *
     * Used in:
     * @PreAuthorize("@userSecurity.isOwner(authentication, #orderId)")
     */
    public boolean isOwner(Authentication authentication, Long orderId) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Order order = orderRepository.findById(orderId)
                .orElse(null);

        if (order == null) {
            return false;
        }

        return order.getOwnerUsername()
                .equals(authentication.getName());
    }
}