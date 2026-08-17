package com.example.ecommerce.order;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.cart.Cart;
import com.example.ecommerce.cart.CartItem;
import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.order.dto.OrderDtos;
import com.example.ecommerce.outbox.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Serwis domenowy odpowiedzialny za zamówienia.
 *
 * To tutaj koszyk zostaje zamieniony w zamówienie.
 *
 * Serwis odpowiada za:
 * - utworzenie zamówienia w statusie oczekującym na płatność,
 * - zapis snapshotów produktów i cen,
 * - pobieranie zamówień użytkownika,
 * - kontrolę dostępu do zamówienia,
 * - anulowanie zamówienia,
 * - mapowanie encji zamówienia na DTO,
 * - publikację eventów domenowych przez outbox.
 */
@Service
public class OrderService {

    /**
     * Repozytorium zamówień.
     *
     * Używane do tworzenia, pobierania i listowania CustomerOrder.
     */
    private final CustomerOrderRepository orders;

    /**
     * Serwis outbox.
     *
     * Po ważnych zmianach w zamówieniu zapisujemy eventy domenowe,
     * np. OrderCreated albo OrderCancelled.
     *
     * Dzięki temu inne procesy mogą zareagować asynchronicznie:
     * - notification-service,
     * - ERP,
     * - WMS,
     * - analytics,
     * - fulfillment.
     */
    private final OutboxService outbox;

    /**
     * Constructor injection.
     */
    public OrderService(
            CustomerOrderRepository orders,
            OutboxService outbox
    ) {
        this.orders = orders;
        this.outbox = outbox;
    }

    /**
     * Tworzy zamówienie na podstawie aktywnego koszyka użytkownika.
     *
     * Ta metoda jest wywoływana przez CheckoutService.
     *
     * Flow:
     * 1. Policz subtotal z pozycji koszyka.
     * 2. Dodaj koszt dostawy.
     * 3. Ustal walutę.
     * 4. Utwórz CustomerOrder.
     * 5. Przepisz pozycje koszyka jako CustomerOrderItem.
     * 6. Zapisz zamówienie.
     * 7. Opublikuj event OrderCreated do outboxa.
     *
     * Ważne:
     * Zamówienie przechowuje snapshot danych z koszyka.
     * Nie powinno zależeć od późniejszych zmian nazwy produktu, SKU albo ceny.
     */
    @Transactional
    public CustomerOrder createPendingOrder(
            AppUser user,
            Cart cart,
            String shippingAddress,
            String billingAddress,
            String shippingMethod
    ) {
        /*
         * Subtotal liczymy z lineTotal każdej pozycji koszyka.
         *
         * lineTotal zwykle oznacza:
         * unitPriceSnapshot * quantity
         */
        BigDecimal subtotal = cart.getItems()
                .stream()
                .map(CartItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        /*
         * Stały koszt dostawy dla MVP.
         *
         * W produkcyjnym systemie shipping powinien być liczony przez osobny moduł,
         * zależnie od kraju, metody dostawy, wagi, gabarytów, koszyka i promocji.
         */
        BigDecimal shipping = BigDecimal.valueOf(15);

        /*
         * Total to suma produktów i dostawy.
         *
         * W kolejnych etapach tutaj powinny wejść:
         * - promocje,
         * - kupony,
         * - punkty lojalnościowe,
         * - podatki,
         * - dynamic pricing,
         * - opłaty marketplace.
         */
        BigDecimal total = subtotal.add(shipping);

        /*
         * Waluta jest brana z pierwszej pozycji koszyka.
         *
         * Jeśli koszyk jest pusty, domyślnie PLN.
         *
         * CheckoutService wcześniej powinien blokować pusty koszyk,
         * więc fallback PLN jest głównie zabezpieczeniem technicznym.
         */
        String currency = cart.getItems().isEmpty()
                ? "PLN"
                : cart.getItems().get(0).getCurrency();

        /*
         * Tworzymy zamówienie w statusie początkowym.
         *
         * Numer zamówienia jest prosty dla MVP:
         * ORD-{timestamp}
         *
         * W produkcji lepiej użyć dedykowanej sekwencji lub generatora numerów,
         * odpornego na kolizje i zgodnego z wymaganiami biznesowymi.
         */
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

        /*
         * Przenosimy pozycje koszyka do zamówienia.
         *
         * Kluczowe:
         * zapisujemy snapshot danych produktu:
         * - productId,
         * - variantId,
         * - SKU,
         * - nazwę produktu,
         * - nazwę wariantu,
         * - ilość,
         * - cenę jednostkową,
         * - line total.
         *
         * Dzięki temu zamówienie pozostaje historycznie poprawne,
         * nawet jeśli później admin zmieni nazwę, SKU albo cenę produktu.
         */
        for (CartItem cartItem : cart.getItems()) {
            var variant = cartItem.getVariant();

            order.addItem(
                    new CustomerOrderItem(
                            variant.getProduct().getId(),
                            variant.getId(),
                            variant.getSku(),
                            variant.getProduct().getName(),
                            variant.getName(),
                            cartItem.getQuantity(),
                            cartItem.getUnitPriceSnapshot(),
                            cartItem.lineTotal()
                    )
            );
        }

        /*
         * Zapis zamówienia razem z pozycjami.
         *
         * Encja CustomerOrder powinna mieć relację cascade do CustomerOrderItem.
         */
        CustomerOrder saved = orders.save(order);

        /*
         * Event OrderCreated.
         *
         * To główny sygnał dla reszty systemu, że powstało nowe zamówienie.
         *
         * Może zostać obsłużony przez:
         * - notification-service,
         * - ERP sync,
         * - WMS,
         * - analytics,
         * - marketplace settlement.
         */
        outbox.saveEvent(
                "Order",
                saved.getId().toString(),
                "OrderCreated",
                Map.of(
                        "orderId", saved.getId(),
                        "orderNumber", saved.getOrderNumber(),
                        "userId", user.getId()
                )
        );

        return saved;
    }

    /**
     * Pobiera encję zamówienia należącego do konkretnego użytkownika.
     *
     * To ważna metoda bezpieczeństwa.
     *
     * Nie pobieramy zamówienia samym orderId.
     * Pobieramy je po orderId + userId.
     *
     * Dzięki temu użytkownik nie może podejrzeć ani użyć cudzego zamówienia,
     * nawet jeśli zna jego ID.
     */
    @Transactional(readOnly = true)
    public CustomerOrder getOrderEntityForUser(AppUser user, Long orderId) {
        return orders.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
    }

    /**
     * Zwraca listę zamówień aktualnie zalogowanego użytkownika.
     *
     * Zamówienia są sortowane od najnowszych.
     *
     * Używane przez endpoint:
     * GET /api/orders
     */
    @Transactional(readOnly = true)
    public List<OrderDtos.OrderResponse> userOrders(AppUser user) {
        return orders.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Zwraca wszystkie zamówienia.
     *
     * To metoda dla panelu admina.
     *
     * Nie filtruje po użytkowniku, więc nie powinna być wystawiona
     * w publicznym API klienta.
     */
    @Transactional(readOnly = true)
    public List<OrderDtos.OrderResponse> allOrders() {
        return orders.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Zwraca szczegóły jednego zamówienia użytkownika.
     *
     * Tak samo jak getOrderEntityForUser, kontroluje własność przez:
     * orderId + userId.
     *
     * Jeśli zamówienie nie istnieje albo należy do innego użytkownika,
     * zwracamy 404.
     *
     * 404 zamiast 403 ogranicza ujawnianie informacji,
     * czy dane orderId w ogóle istnieje.
     */
    @Transactional(readOnly = true)
    public OrderDtos.OrderResponse userOrder(AppUser user, Long orderId) {
        CustomerOrder order = orders.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));

        return toResponse(order);
    }

    /**
     * Anuluje zamówienie użytkownika.
     *
     * Flow:
     * 1. Pobierz zamówienie po orderId + userId.
     * 2. Sprawdź, czy status pozwala na anulowanie.
     * 3. Zmień status zamówienia na CANCELLED.
     * 4. Zapisz event OrderCancelled do outboxa.
     * 5. Zwróć aktualny stan zamówienia.
     *
     * W tej wersji anulować można tylko:
     * - PENDING_PAYMENT,
     * - PAYMENT_FAILED.
     *
     * Zamówienia opłacone lub wysłane zwykle powinny przejść przez proces zwrotu,
     * a nie proste anulowanie.
     */
    @Transactional
    public OrderDtos.OrderResponse cancelOrder(AppUser user, Long orderId) {
        CustomerOrder order = orders.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));

        /*
         * Walidacja statusu.
         *
         * Nie każde zamówienie może zostać anulowane.
         */
        if (
                order.getStatus() != OrderStatus.PENDING_PAYMENT
                        && order.getStatus() != OrderStatus.PAYMENT_FAILED
        ) {
            throw ApiException.badRequest(
                    "Order cannot be cancelled in status " + order.getStatus()
            );
        }

        /*
         * Zmiana statusu jest zamknięta w encji CustomerOrder.
         */
        order.cancel();

        /*
         * Event OrderCancelled.
         *
         * Downstream procesy mogą na niego zareagować:
         * - zwolnić rezerwacje inventory,
         * - zatrzymać fulfillment,
         * - wysłać e-mail,
         * - zsynchronizować status z ERP.
         *
         * Uwaga:
         * Jeśli anulowanie ma od razu zwalniać stock, warto jawnie wywołać
         * InventoryService.releaseReservations(orderId) albo obsłużyć event
         * przez worker/event handler.
         */
        outbox.saveEvent(
                "Order",
                order.getId().toString(),
                "OrderCancelled",
                Map.of(
                        "orderId", order.getId(),
                        "orderNumber", order.getOrderNumber()
                )
        );

        return toResponse(order);
    }

    /**
     * Mapuje encję CustomerOrder na DTO odpowiedzi API.
     *
     * DTO zawiera:
     * - ID zamówienia,
     * - numer zamówienia,
     * - status,
     * - kwoty,
     * - walutę,
     * - adresy,
     * - metodę dostawy,
     * - datę utworzenia,
     * - pozycje zamówienia.
     *
     * Nie zwracamy encji JPA bezpośrednio do API.
     */
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
                order.getItems()
                        .stream()
                        .map(this::toItemResponse)
                        .toList()
        );
    }

    /**
     * Mapuje pojedynczą pozycję zamówienia na DTO.
     *
     * Pozycja zamówienia bazuje na snapshotach zapisanych w momencie checkoutu.
     *
     * To znaczy, że odpowiedź pokazuje historyczny stan zakupu,
     * a nie aktualną nazwę lub cenę produktu z katalogu.
     */
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