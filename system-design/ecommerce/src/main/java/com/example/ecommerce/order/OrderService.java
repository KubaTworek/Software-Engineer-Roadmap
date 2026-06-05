package com.example.ecommerce.order;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.cart.Cart;
import com.example.ecommerce.cart.CartItem;
import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.order.dto.OrderDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class OrderService {
    private final CustomerOrderRepository orders;

    public OrderService(CustomerOrderRepository orders) {
        this.orders = orders;
    }

    @Transactional
    public CustomerOrder createPendingOrder(
            AppUser user,
            Cart cart,
            String shippingAddress,
            String billingAddress,
            String shippingMethod
    ) {
        BigDecimal subtotal = cart.getItems()
                .stream()
                .map(CartItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shipping = BigDecimal.valueOf(15);
        BigDecimal total = subtotal.add(shipping);
        String currency = cart.getItems().isEmpty() ? "PLN" : cart.getItems().get(0).getCurrency();

        CustomerOrder order = new CustomerOrder(
                "ORD-" + Instant.now().toEpochMilli(),
                user,
                subtotal,
                shipping,
                total,
                currency,
                shippingAddress,
                billingAddress,
                shippingMethod
        );

        for (CartItem cartItem : cart.getItems()) {
            var variant = cartItem.getVariant();
            order.addItem(new CustomerOrderItem(
                    variant.getProduct().getId(),
                    variant.getId(),
                    variant.getSku(),
                    variant.getProduct().getName(),
                    variant.getName(),
                    cartItem.getQuantity(),
                    cartItem.getUnitPriceSnapshot(),
                    cartItem.lineTotal()
            ));
        }

        return orders.save(order);
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.OrderResponse> userOrders(AppUser user) {
        return orders.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.OrderResponse> allOrders() {
        return orders.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderResponse userOrder(AppUser user, Long orderId) {
        CustomerOrder order = orders.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        return toResponse(order);
    }

    @Transactional
    public OrderDtos.OrderResponse cancelOrder(AppUser user, Long orderId) {
        CustomerOrder order = orders.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            throw ApiException.badRequest("Order cannot be cancelled in status " + order.getStatus());
        }

        order.cancel();
        return toResponse(order);
    }

    public OrderDtos.OrderResponse toResponse(CustomerOrder order) {
        return new OrderDtos.OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getSubtotalAmount(),
                order.getShippingAmount(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getShippingAddress(),
                order.getBillingAddress(),
                order.getShippingMethod(),
                order.getCreatedAt(),
                order.getItems().stream().map(this::toItemResponse).toList()
        );
    }

    private OrderDtos.OrderItemResponse toItemResponse(CustomerOrderItem item) {
        return new OrderDtos.OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductVariantId(),
                item.getSku(),
                item.getProductNameSnapshot(),
                item.getVariantNameSnapshot(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal()
        );
    }
}
