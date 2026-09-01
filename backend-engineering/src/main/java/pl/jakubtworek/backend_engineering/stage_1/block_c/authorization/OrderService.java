package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Demonstrates method-level authorization with @PreAuthorize.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
    }

    /**
     * Access isAllowed when user has exact authority ORDER_READ
     * or role ADMIN.
     *
     * hasRole("ADMIN") checks for authority ROLE_ADMIN.
     */
    @PreAuthorize("hasAuthority('ORDER_READ') or hasRole('ADMIN')")
    public Order getOrder(Long id) {
        return orderRepository.findById(requirePositive(id, "id"))
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + id));
    }

    /**
     * Data-based authorization.
     *
     * The user can update order only if:
     * - user owns the order,
     * - or user has ADMIN role.
     */
    @PreAuthorize("@userSecurity.isOwner(authentication, #orderId) or hasRole('ADMIN')")
    public Order updateOrder(Long orderId, String description) {
        Long validatedOrderId = requirePositive(orderId, "orderId");
        Order order = orderRepository.findById(validatedOrderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + validatedOrderId));
        order.updateDescription(description);
        return orderRepository.save(order);
    }

    /**
     * Only ADMIN role can delete orders.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteOrder(Long orderId) {
        orderRepository.deleteById(requirePositive(orderId, "orderId"));
    }

    private static Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }
}
