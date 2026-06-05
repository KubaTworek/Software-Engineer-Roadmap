package com.example.ecommerce.cart;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.cart.dto.CartDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService carts;

    public CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping
    public CartDtos.CartResponse getCart(@AuthenticationPrincipal AppUser user) {
        return carts.getCart(user);
    }

    @PostMapping("/items")
    public CartDtos.CartResponse addItem(
            @AuthenticationPrincipal AppUser user,
            @Valid @RequestBody CartDtos.AddCartItemRequest request
    ) {
        return carts.addItem(user, request);
    }

    @PatchMapping("/items/{itemId}")
    public CartDtos.CartResponse updateItem(
            @AuthenticationPrincipal AppUser user,
            @PathVariable Long itemId,
            @Valid @RequestBody CartDtos.UpdateCartItemRequest request
    ) {
        return carts.updateItem(user, itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public CartDtos.CartResponse removeItem(
            @AuthenticationPrincipal AppUser user,
            @PathVariable Long itemId
    ) {
        return carts.removeItem(user, itemId);
    }
}
