package com.example.ecommerce.checkout;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.cart.Cart;
import com.example.ecommerce.cart.CartService;
import com.example.ecommerce.checkout.dto.CheckoutDtos;
import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.inventory.InventoryService;
import com.example.ecommerce.order.CustomerOrder;
import com.example.ecommerce.order.OrderService;
import com.example.ecommerce.payment.Payment;
import com.example.ecommerce.payment.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CheckoutService {
    private final CartService carts;
    private final OrderService orders;
    private final InventoryService inventory;
    private final PaymentService payments;

    public CheckoutService(
            CartService carts,
            OrderService orders,
            InventoryService inventory,
            PaymentService payments
    ) {
        this.carts = carts;
        this.orders = orders;
        this.inventory = inventory;
        this.payments = payments;
    }

    @Transactional
    public CheckoutDtos.CheckoutResponse checkout(AppUser user, CheckoutDtos.CheckoutRequest request) {
        Cart cart = carts.getOrCreateActiveCart(user);

        if (cart.getItems().isEmpty()) {
            throw ApiException.badRequest("Cannot checkout empty cart");
        }

        CustomerOrder order = orders.createPendingOrder(
                user,
                cart,
                request.shippingAddress(),
                request.billingAddress(),
                request.shippingMethod()
        );

        for (var item : cart.getItems()) {
            inventory.reserve(order.getId(), item.getVariant(), item.getQuantity());
        }

        Payment payment = payments.createPayment(order, "checkout-" + order.getId() + "-" + UUID.randomUUID());
        cart.markCheckedOut();

        return new CheckoutDtos.CheckoutResponse(
                orders.toResponse(order),
                payments.toResponse(payment)
        );
    }
}
