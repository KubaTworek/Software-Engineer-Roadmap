package com.example.ecommerce.cart;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.cart.dto.CartDtos;
import com.example.ecommerce.catalog.CatalogService;
import com.example.ecommerce.catalog.ProductVariant;
import com.example.ecommerce.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CartService {
    private final CartRepository carts;
    private final CartItemRepository items;
    private final CatalogService catalog;

    public CartService(CartRepository carts, CartItemRepository items, CatalogService catalog) {
        this.carts = carts;
        this.items = items;
        this.catalog = catalog;
    }

    @Transactional
    public Cart getOrCreateActiveCart(AppUser user) {
        return carts.findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), CartStatus.ACTIVE)
                .orElseGet(() -> carts.save(new Cart(user)));
    }

    @Transactional
    public CartDtos.CartResponse getCart(AppUser user) {
        return toResponse(getOrCreateActiveCart(user));
    }

    @Transactional
    public CartDtos.CartResponse addItem(AppUser user, CartDtos.AddCartItemRequest request) {
        Cart cart = getOrCreateActiveCart(user);
        ProductVariant variant = catalog.getActiveVariant(request.productVariantId());

        var existing = cart.getItems()
                .stream()
                .filter(item -> item.getVariant().getId().equals(variant.getId()))
                .findFirst();

        if (existing.isPresent()) {
            existing.get().setQuantity(existing.get().getQuantity() + request.quantity());
        } else {
            cart.addItem(new CartItem(
                    variant,
                    request.quantity(),
                    variant.getPrice(),
                    variant.getCurrency()
            ));
        }

        return toResponse(cart);
    }

    @Transactional
    public CartDtos.CartResponse updateItem(AppUser user, Long itemId, CartDtos.UpdateCartItemRequest request) {
        CartItem item = items.findByIdAndCartUserId(itemId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Cart item not found"));

        item.setQuantity(request.quantity());
        return toResponse(item.getCart());
    }

    @Transactional
    public CartDtos.CartResponse removeItem(AppUser user, Long itemId) {
        Cart cart = getOrCreateActiveCart(user);
        CartItem item = items.findByIdAndCartUserId(itemId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Cart item not found"));

        cart.getItems().remove(item);
        items.delete(item);

        return toResponse(cart);
    }

    public CartDtos.CartResponse toResponse(Cart cart) {
        var itemResponses = cart.getItems()
                .stream()
                .map(this::toItemResponse)
                .toList();

        BigDecimal subtotal = itemResponses
                .stream()
                .map(CartDtos.CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String currency = itemResponses.isEmpty() ? "PLN" : itemResponses.get(0).currency();

        return new CartDtos.CartResponse(cart.getId(), itemResponses, subtotal, currency);
    }

    private CartDtos.CartItemResponse toItemResponse(CartItem item) {
        ProductVariant variant = item.getVariant();
        return new CartDtos.CartItemResponse(
                item.getId(),
                variant.getProduct().getId(),
                variant.getId(),
                variant.getProduct().getName(),
                variant.getName(),
                variant.getSku(),
                item.getQuantity(),
                item.getUnitPriceSnapshot(),
                item.lineTotal(),
                item.getCurrency()
        );
    }
}
