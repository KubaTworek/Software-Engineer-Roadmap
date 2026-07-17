package com.example.ecommerce.cart;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.cart.dto.CartDtos;
import com.example.ecommerce.catalog.CatalogService;
import com.example.ecommerce.catalog.ProductVariant;
import com.example.ecommerce.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Serwis domenowy odpowiedzialny za logikę koszyka użytkownika.
 *
 * To tutaj znajduje się właściwa logika biznesowa koszyka:
 * - pobranie lub utworzenie aktywnego koszyka,
 * - dodanie produktu,
 * - zwiększenie ilości istniejącej pozycji,
 * - aktualizacja ilości,
 * - usunięcie pozycji,
 * - przeliczenie subtotalu,
 * - zbudowanie odpowiedzi dla API.
 *
 * Controller tylko przyjmuje request HTTP.
 * Ten serwis decyduje, co faktycznie dzieje się z koszykiem.
 */
@Service
public class CartService {

    /**
     * Repozytorium koszyków.
     *
     * Służy głównie do pobrania aktywnego koszyka użytkownika
     * albo utworzenia nowego, jeśli użytkownik jeszcze go nie ma.
     */
    private final CartRepository carts;

    /**
     * Repozytorium pozycji koszyka.
     *
     * Używane przy operacjach na konkretnej pozycji koszyka:
     * update quantity oraz remove item.
     */
    private final CartItemRepository items;

    /**
     * Serwis katalogu produktów.
     *
     * Koszyk nie powinien samodzielnie ufać productVariantId z requestu.
     * Najpierw sprawdzamy w katalogu, czy wariant produktu istnieje,
     * jest aktywny i może zostać dodany do koszyka.
     */
    private final CatalogService catalog;

    /**
     * Constructor injection.
     *
     * Zależności są jawne, finalne i łatwe do podstawienia w testach.
     */
    public CartService(CartRepository carts, CartItemRepository items, CatalogService catalog) {
        this.carts = carts;
        this.items = items;
        this.catalog = catalog;
    }

    /**
     * Pobiera aktywny koszyk użytkownika albo tworzy nowy.
     *
     * To jest centralna metoda dla koszyka, bo większość operacji
     * wymaga pracy na aktywnym koszyku użytkownika.
     *
     * Szukamy ostatniego aktywnego koszyka użytkownika.
     * Jeśli go nie ma, tworzymy nowy koszyk powiązany z tym użytkownikiem.
     *
     * @Transactional jest ważne, bo metoda może wykonać zapis do bazy
     * w przypadku tworzenia nowego koszyka.
     */
    @Transactional
    public Cart getOrCreateActiveCart(AppUser user) {
        return carts.findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), CartStatus.ACTIVE)
                .orElseGet(() -> carts.save(new Cart(user)));
    }

    /**
     * Zwraca aktualny koszyk użytkownika w formacie DTO dla API.
     *
     * Jeśli koszyk jeszcze nie istnieje, zostanie utworzony pusty koszyk.
     * Dzięki temu frontend zawsze dostaje spójną odpowiedź, zamiast obsługiwać null.
     */
    @Transactional
    public CartDtos.CartResponse getCart(AppUser user) {
        return toResponse(getOrCreateActiveCart(user));
    }

    /**
     * Dodaje wariant produktu do koszyka.
     *
     * Kluczowy flow:
     * 1. Pobierz albo utwórz aktywny koszyk użytkownika.
     * 2. Sprawdź w CatalogService, czy wariant produktu jest aktywny.
     * 3. Jeśli ten wariant już jest w koszyku, zwiększ jego ilość.
     * 4. Jeśli go nie ma, dodaj nową pozycję koszyka.
     * 5. Zwróć przeliczony stan koszyka.
     *
     * Ważne:
     * Cena nie przychodzi z frontendu.
     * Cena jest brana z ProductVariant i zapisywana jako unitPriceSnapshot.
     *
     * Dzięki snapshotowi koszyk zachowuje cenę z momentu dodania produktu,
     * ale finalny checkout i tak powinien ponownie przeliczyć aktualne ceny.
     */
    @Transactional
    public CartDtos.CartResponse addItem(AppUser user, CartDtos.AddCartItemRequest request) {
        Cart cart = getOrCreateActiveCart(user);

        ProductVariant variant = catalog.getActiveVariant(request.productVariantId());

        var existing = cart.getItems()
                .stream()
                .filter(item -> item.getVariant().getId().equals(variant.getId()))
                .findFirst();

        if (existing.isPresent()) {
            /*
             * Jeśli produkt już jest w koszyku, nie tworzymy duplikatu pozycji.
             * Zwiększamy ilość istniejącej pozycji.
             *
             * To upraszcza frontend i checkout, bo jeden wariant produktu
             * występuje w koszyku tylko raz.
             */
            existing.get().setQuantity(existing.get().getQuantity() + request.quantity());
        } else {
            /*
             * Nowa pozycja koszyka.
             *
             * unitPriceSnapshot i currency zapisują stan ceny w momencie dodania.
             * To jest ważne dla historii koszyka, ale nie powinno zastępować
             * finalnego przeliczenia ceny w checkout.
             */
            cart.addItem(new CartItem(
                    variant,
                    request.quantity(),
                    variant.getPrice(),
                    variant.getCurrency()
            ));
        }

        return toResponse(cart);
    }

    /**
     * Aktualizuje ilość konkretnej pozycji koszyka.
     *
     * Bardzo ważne zabezpieczenie:
     * pozycja jest wyszukiwana po itemId oraz userId.
     *
     * Dzięki temu użytkownik nie może zmodyfikować pozycji koszyka,
     * która należy do innego użytkownika, nawet jeśli odgadnie jej ID.
     */
    @Transactional
    public CartDtos.CartResponse updateItem(AppUser user, Long itemId, CartDtos.UpdateCartItemRequest request) {
        CartItem item = items.findByIdAndCartUserId(itemId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Cart item not found"));

        item.setQuantity(request.quantity());

        return toResponse(item.getCart());
    }

    /**
     * Usuwa pozycję z koszyka użytkownika.
     *
     * Najpierw pobierany jest aktywny koszyk użytkownika.
     * Następnie pozycja jest wyszukiwana po itemId oraz userId,
     * żeby nie dopuścić do usunięcia cudzej pozycji koszyka.
     *
     * Pozycja jest usuwana zarówno z kolekcji cart.getItems(),
     * jak i przez repozytorium items.delete(item).
     */
    @Transactional
    public CartDtos.CartResponse removeItem(AppUser user, Long itemId) {
        Cart cart = getOrCreateActiveCart(user);

        CartItem item = items.findByIdAndCartUserId(itemId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Cart item not found"));

        cart.getItems().remove(item);
        items.delete(item);

        return toResponse(cart);
    }

    /**
     * Mapuje encję Cart na DTO zwracane przez API.
     *
     * Ta metoda:
     * - mapuje każdą pozycję koszyka na CartItemResponse,
     * - liczy subtotal,
     * - ustala walutę koszyka,
     * - zwraca gotowy obiekt odpowiedzi.
     *
     * Subtotal jest liczony z lineTotal pozycji koszyka.
     */
    public CartDtos.CartResponse toResponse(Cart cart) {
        var itemResponses = cart.getItems()
                .stream()
                .map(this::toItemResponse)
                .toList();

        BigDecimal subtotal = itemResponses
                .stream()
                .map(CartDtos.CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        /*
         * Jeśli koszyk jest pusty, domyślnie zwracamy PLN.
         *
         * Jeśli koszyk ma pozycje, waluta jest brana z pierwszej pozycji.
         * W produkcyjnym systemie warto wymusić jedną walutę dla całego koszyka
         * albo jawnie walidować, że wszystkie pozycje mają tę samą walutę.
         */
        String currency = itemResponses.isEmpty() ? "PLN" : itemResponses.get(0).currency();

        return new CartDtos.CartResponse(cart.getId(), itemResponses, subtotal, currency);
    }

    /**
     * Mapuje pojedynczą pozycję koszyka na DTO.
     *
     * Odpowiedź zawiera dane potrzebne frontendowi:
     * - ID pozycji koszyka,
     * - ID produktu,
     * - ID wariantu,
     * - nazwę produktu,
     * - nazwę wariantu,
     * - SKU,
     * - ilość,
     * - cenę jednostkową,
     * - wartość linii,
     * - walutę.
     *
     * Dane produktu są pobierane z powiązanego ProductVariant.
     */
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