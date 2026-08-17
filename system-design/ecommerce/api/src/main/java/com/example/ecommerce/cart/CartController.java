package com.example.ecommerce.cart;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.cart.dto.CartDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller odpowiedzialny za operacje na koszyku aktualnie zalogowanego użytkownika.
 *
 * Ta klasa nie zawiera logiki biznesowej koszyka — tylko mapuje HTTP API na metody CartService.
 * Logika typu: utworzenie aktywnego koszyka, dodanie produktu, walidacja wariantu,
 * zmiana ilości czy usunięcie pozycji powinna znajdować się w CartService.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    /**
     * Serwis domenowy koszyka.
     *
     * Controller deleguje do niego wszystkie operacje, dzięki czemu warstwa HTTP
     * nie miesza się z logiką biznesową.
     */
    private final CartService carts;

    /**
     * Constructor injection.
     *
     * Preferowane podejście w Springu, bo zależność jest jawna, finalna
     * i łatwa do podstawienia w testach.
     */
    public CartController(CartService carts) {
        this.carts = carts;
    }

    /**
     * Zwraca aktywny koszyk aktualnie zalogowanego użytkownika.
     *
     * @AuthenticationPrincipal AppUser user:
     * Spring Security wstrzykuje tutaj użytkownika odczytanego z tokena Bearer.
     * Dzięki temu klient API nie podaje userId w requestach, co chroni przed
     * dostępem do koszyka innego użytkownika.
     *
     * GET /api/cart
     */
    @GetMapping
    public CartDtos.CartResponse getCart(@AuthenticationPrincipal AppUser user) {
        return carts.getCart(user);
    }

    /**
     * Dodaje produkt/wariant produktu do koszyka.
     *
     * Request zawiera productVariantId oraz quantity.
     *
     * @Valid uruchamia walidację DTO, np. czy quantity >= 1
     * i czy productVariantId nie jest nullem.
     *
     * Kluczowe:
     * - frontend nie powinien wysyłać ceny,
     * - cena powinna być pobrana po stronie backendu z katalogu,
     * - CartService powinien sprawdzić, czy wariant produktu istnieje i jest aktywny.
     *
     * POST /api/cart/items
     */
    @PostMapping("/items")
    public CartDtos.CartResponse addItem(
            @AuthenticationPrincipal AppUser user,
            @Valid @RequestBody CartDtos.AddCartItemRequest request
    ) {
        return carts.addItem(user, request);
    }

    /**
     * Zmienia ilość istniejącej pozycji w koszyku.
     *
     * itemId pochodzi ze ścieżki URL i identyfikuje konkretną pozycję koszyka.
     *
     * Bardzo ważne:
     * CartService musi sprawdzić, czy ta pozycja należy do aktualnie zalogowanego użytkownika.
     * Samo itemId nie wystarcza, bo inaczej użytkownik mógłby próbować modyfikować
     * cudze pozycje koszyka.
     *
     * PATCH /api/cart/items/{itemId}
     */
    @PatchMapping("/items/{itemId}")
    public CartDtos.CartResponse updateItem(
            @AuthenticationPrincipal AppUser user,
            @PathVariable Long itemId,
            @Valid @RequestBody CartDtos.UpdateCartItemRequest request
    ) {
        return carts.updateItem(user, itemId, request);
    }

    /**
     * Usuwa pozycję z koszyka.
     *
     * Podobnie jak przy aktualizacji, CartService powinien sprawdzić własność pozycji:
     * itemId musi należeć do koszyka aktualnie zalogowanego użytkownika.
     *
     * Po usunięciu zwracany jest aktualny stan koszyka, żeby frontend mógł
     * od razu odświeżyć widok bez dodatkowego GET /api/cart.
     *
     * DELETE /api/cart/items/{itemId}
     */
    @DeleteMapping("/items/{itemId}")
    public CartDtos.CartResponse removeItem(
            @AuthenticationPrincipal AppUser user,
            @PathVariable Long itemId
    ) {
        return carts.removeItem(user, itemId);
    }
}