package com.example.ecommerce.order;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.order.dto.OrderDtos;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller odpowiedzialny za API zamówień aktualnie zalogowanego użytkownika.
 *
 * Udostępnia operacje:
 * - pobranie listy własnych zamówień,
 * - pobranie szczegółów konkretnego zamówienia,
 * - anulowanie zamówienia.
 *
 * Controller nie zawiera logiki biznesowej zamówień.
 * Deleguje ją do OrderService.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /**
     * Serwis zamówień.
     *
     * Odpowiada za właściwą logikę:
     * - pobieranie zamówień użytkownika,
     * - kontrolę własności zamówienia,
     * - mapowanie zamówienia na DTO,
     * - anulowanie zamówienia,
     * - zwalnianie rezerwacji inventory,
     * - publikację eventów domenowych.
     */
    private final OrderService orders;

    /**
     * Constructor injection.
     *
     * Controller potrzebuje tylko OrderService.
     * Nie powinien bezpośrednio korzystać z repozytorium zamówień,
     * bo kontrola dostępu i logika statusów należy do serwisu.
     */
    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /**
     * Zwraca listę zamówień aktualnie zalogowanego użytkownika.
     *
     * Endpoint:
     * GET /api/orders
     *
     * @AuthenticationPrincipal AppUser user:
     * użytkownik jest pobierany z kontekstu bezpieczeństwa.
     *
     * Dzięki temu klient API nie przekazuje userId w requestcie.
     * To ważne, bo zamówienia są prywatnymi danymi użytkownika.
     */
    @GetMapping
    public List<OrderDtos.OrderResponse> orders(
            @AuthenticationPrincipal AppUser user
    ) {
        return orders.userOrders(user);
    }

    /**
     * Zwraca szczegóły jednego zamówienia użytkownika.
     *
     * Endpoint:
     * GET /api/orders/{orderId}
     *
     * orderId pochodzi ze ścieżki URL.
     *
     * Kluczowe:
     * OrderService musi sprawdzić, czy zamówienie o podanym orderId
     * należy do aktualnie zalogowanego użytkownika.
     *
     * Sam fakt, że klient zna orderId, nie może dawać dostępu do zamówienia.
     */
    @GetMapping("/{orderId}")
    public OrderDtos.OrderResponse order(
            @AuthenticationPrincipal AppUser user,
            @PathVariable Long orderId
    ) {
        return orders.userOrder(user, orderId);
    }

    /**
     * Anuluje zamówienie użytkownika.
     *
     * Endpoint:
     * POST /api/orders/{orderId}/cancel
     *
     * To operacja zmieniająca stan systemu, dlatego używamy POST,
     * a nie GET.
     *
     * Kluczowe:
     * - użytkownik może anulować tylko własne zamówienie,
     * - OrderService powinien sprawdzić, czy status zamówienia pozwala na anulowanie,
     * - przy anulowaniu trzeba zwolnić aktywne rezerwacje inventory,
     * - warto opublikować event OrderCancelled do outboxa.
     *
     * Przykładowo nie każde zamówienie powinno dać się anulować:
     * - opłacone i wysłane zamówienie zwykle wymaga procesu zwrotu,
     * - zamówienie już anulowane nie powinno być anulowane drugi raz.
     */
    @PostMapping("/{orderId}/cancel")
    public OrderDtos.OrderResponse cancel(
            @AuthenticationPrincipal AppUser user,
            @PathVariable Long orderId
    ) {
        return orders.cancelOrder(user, orderId);
    }
}