package com.example.ecommerce.order;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.order.dto.OrderDtos;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public List<OrderDtos.OrderResponse> orders(@AuthenticationPrincipal AppUser user) {
        return orders.userOrders(user);
    }

    @GetMapping("/{orderId}")
    public OrderDtos.OrderResponse order(@AuthenticationPrincipal AppUser user, @PathVariable Long orderId) {
        return orders.userOrder(user, orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderDtos.OrderResponse cancel(@AuthenticationPrincipal AppUser user, @PathVariable Long orderId) {
        return orders.cancelOrder(user, orderId);
    }
}
