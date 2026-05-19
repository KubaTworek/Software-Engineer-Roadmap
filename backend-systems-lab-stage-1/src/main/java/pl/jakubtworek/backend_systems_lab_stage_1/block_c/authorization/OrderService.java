package pl.jakubtworek.backend_systems_lab_stage_1.block_c.authorization;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Demonstrates method-level authorization with @PreAuthorize.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Access allowed when user has exact authority ORDER_READ
     * or role ADMIN.
     *
     * hasRole("ADMIN") checks for authority ROLE_ADMIN.
     */
    @PreAuthorize("hasAuthority('ORDER_READ') or hasRole('ADMIN')")
    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow();
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

        Order order = orderRepository.findById(orderId)
                .orElseThrow();

        /**
         * Update logic omitted for simplicity.
         */
        return order;
    }

    /**
     * Only ADMIN role can delete orders.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteOrder(Long orderId) {
        orderRepository.deleteById(orderId);
    }
}