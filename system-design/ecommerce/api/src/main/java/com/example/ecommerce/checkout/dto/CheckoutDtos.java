package com.example.ecommerce.checkout.dto;

import com.example.ecommerce.order.dto.OrderDtos;
import com.example.ecommerce.payment.dto.PaymentDtos;
import jakarta.validation.constraints.NotBlank;

public final class CheckoutDtos {
    private CheckoutDtos() {}

    public record CheckoutRequest(
            @NotBlank String shippingAddress,
            @NotBlank String billingAddress,
            @NotBlank String shippingMethod
    ) {}

    public record CheckoutResponse(
            OrderDtos.OrderResponse order,
            PaymentDtos.PaymentResponse payment
    ) {}
}
