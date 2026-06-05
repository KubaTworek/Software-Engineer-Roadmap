package com.example.ecommerce.checkout;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.checkout.dto.CheckoutDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {
    private final CheckoutService checkout;

    public CheckoutController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    @PostMapping
    public CheckoutDtos.CheckoutResponse checkout(
            @AuthenticationPrincipal AppUser user,
            @Valid @RequestBody CheckoutDtos.CheckoutRequest request
    ) {
        return checkout.checkout(user, request);
    }
}
