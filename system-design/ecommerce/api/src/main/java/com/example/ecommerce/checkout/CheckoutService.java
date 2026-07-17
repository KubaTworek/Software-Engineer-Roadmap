package com.example.ecommerce.checkout;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.cart.Cart;
import com.example.ecommerce.cart.CartService;
import com.example.ecommerce.checkout.dto.CheckoutDtos;
import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.idempotency.IdempotencyRecord;
import com.example.ecommerce.idempotency.IdempotencyService;
import com.example.ecommerce.idempotency.IdempotencyStatus;
import com.example.ecommerce.inventory.InventoryService;
import com.example.ecommerce.monitoring.BusinessMetrics;
import com.example.ecommerce.order.CustomerOrder;
import com.example.ecommerce.order.OrderService;
import com.example.ecommerce.payment.Payment;
import com.example.ecommerce.payment.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serwis odpowiedzialny za główny proces checkoutu.
 *
 * Checkout to jeden z najważniejszych procesów w systemie e-commerce.
 * Zamienia aktywny koszyk użytkownika w zamówienie i płatność.
 *
 * Ten serwis koordynuje kilka domen:
 * - Cart — źródło produktów do zakupu,
 * - Order — utworzenie zamówienia,
 * - Inventory — rezerwacja stocku,
 * - Payment — utworzenie płatności,
 * - Idempotency — ochrona przed podwójnym checkoutem,
 * - Monitoring — metryki biznesowe.
 *
 * Ważne:
 * Checkout nie powinien być prostą operacją CRUD.
 * To proces transakcyjny, który musi być odporny na retry, double-clicki,
 * timeouty klienta i częściowe błędy.
 */
@Service
public class CheckoutService {

    /**
     * Nazwa operacji używana w tabeli idempotencji.
     *
     * Ten sam Idempotency-Key może teoretycznie zostać użyty w różnych operacjach,
     * dlatego zapisujemy go razem z typem operacji, tutaj CHECKOUT.
     */
    private static final String IDEMPOTENCY_OPERATION = "CHECKOUT";

    /**
     * Serwis koszyka.
     *
     * Dostarcza aktywny koszyk użytkownika, który ma zostać zamieniony w zamówienie.
     */
    private final CartService carts;

    /**
     * Serwis zamówień.
     *
     * Odpowiada za utworzenie zamówienia w statusie oczekującym na płatność.
     */
    private final OrderService orders;

    /**
     * Serwis inventory.
     *
     * Odpowiada za rezerwację stocku dla pozycji zamówienia.
     *
     * To zabezpiecza przed sprzedażą większej liczby produktów niż dostępna.
     */
    private final InventoryService inventory;

    /**
     * Serwis płatności.
     *
     * Tworzy płatność powiązaną z zamówieniem.
     * W MVP może to być mock payment, a produkcyjnie np. Stripe, PayU, Adyen.
     */
    private final PaymentService payments;

    /**
     * Serwis idempotencji.
     *
     * Chroni checkout przed wykonaniem tej samej operacji więcej niż raz.
     *
     * To jest krytyczne, bo checkout może być ponowiony przez:
     * - frontend,
     * - aplikację mobilną,
     * - API Gateway,
     * - load balancer,
     * - użytkownika klikającego kilka razy.
     */
    private final IdempotencyService idempotency;

    /**
     * Metryki biznesowe.
     *
     * Pozwalają obserwować:
     * - ile checkoutów wystartowało,
     * - ile checkoutów zakończyło się sukcesem.
     */
    private final BusinessMetrics metrics;

    /**
     * Constructor injection.
     *
     * CheckoutService ma sporo zależności, bo koordynuje kilka domen.
     * To normalne dla aplikacyjnego serwisu orkiestrującego proces biznesowy.
     */
    public CheckoutService(
            CartService carts,
            OrderService orders,
            InventoryService inventory,
            PaymentService payments,
            IdempotencyService idempotency,
            BusinessMetrics metrics
    ) {
        this.carts = carts;
        this.orders = orders;
        this.inventory = inventory;
        this.payments = payments;
        this.idempotency = idempotency;
        this.metrics = metrics;
    }

    /**
     * Wykonuje checkout dla aktualnie zalogowanego użytkownika.
     *
     * Cała metoda działa w jednej transakcji.
     *
     * W ramach tej transakcji:
     * - tworzony jest rekord idempotencji,
     * - pobierany jest koszyk,
     * - tworzone jest zamówienie,
     * - rezerwowany jest stock,
     * - tworzona jest płatność,
     * - koszyk oznaczany jest jako CHECKED_OUT,
     * - rekord idempotencji oznaczany jest jako COMPLETED.
     *
     * Dzięki temu albo zapiszą się wszystkie kluczowe zmiany,
     * albo przy błędzie transakcja zostanie wycofana.
     */
    @Transactional
    public CheckoutDtos.CheckoutResponse checkout(
            AppUser user,
            String idempotencyKey,
            CheckoutDtos.CheckoutRequest request
    ) {
        /*
         * Metryka próby checkoutu.
         *
         * Zwiększamy ją na początku, bo interesują nas wszystkie próby,
         * także te zakończone błędem walidacji albo brakiem stocku.
         */
        metrics.checkoutStarted();

        /*
         * Checkout wymaga Idempotency-Key.
         *
         * Bez tego nie mamy bezpiecznego sposobu odróżnienia:
         * - nowego checkoutu,
         * - powtórzonego requestu po timeout,
         * - double-clicka użytkownika.
         */
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.badRequest("Idempotency-Key header is required for checkout");
        }

        /*
         * Hash requestu zabezpiecza przed ponownym użyciem tego samego klucza
         * dla innego body.
         *
         * Przykład:
         * pierwszy request:
         * Idempotency-Key: abc
         * shippingMethod: COURIER_STANDARD
         *
         * drugi request:
         * Idempotency-Key: abc
         * shippingMethod: EXPRESS
         *
         * Taka sytuacja powinna zakończyć się konfliktem, a nie zwróceniem
         * starego zamówienia dla innego requestu.
         */
        String requestHash = idempotency.hashRequest(request);

        /*
         * Pobiera istniejący rekord idempotencji albo tworzy nowy.
         *
         * Rekord jest rozróżniany po:
         * - idempotencyKey,
         * - userId,
         * - operation = CHECKOUT.
         */
        IdempotencyRecord record = idempotency.startOrGet(
                idempotencyKey,
                user.getId(),
                IDEMPOTENCY_OPERATION,
                requestHash
        );

        /*
         * Jeśli checkout z tym kluczem już zakończył się sukcesem,
         * nie tworzymy nowego zamówienia i nowej płatności.
         *
         * Zwracamy poprzedni wynik.
         *
         * To jest sedno idempotencji:
         * ten sam request może zostać bezpiecznie powtórzony,
         * a efekt biznesowy pozostaje jeden.
         */
        if (record.getStatus() == IdempotencyStatus.COMPLETED) {
            CustomerOrder existingOrder = orders.getOrderEntityForUser(user, record.getOrderId());
            Payment existingPayment = payments.getByOrderId(existingOrder.getId());

            return new CheckoutDtos.CheckoutResponse(
                    orders.toResponse(existingOrder),
                    payments.toResponse(existingPayment)
            );
        }

        try {
            /*
             * Pobieramy aktywny koszyk użytkownika.
             *
             * Checkout zawsze działa na koszyku aktualnie zalogowanego usera.
             * Nie przyjmujemy cartId z requestu, więc użytkownik nie może
             * checkoutować cudzego koszyka.
             */
            Cart cart = carts.getOrCreateActiveCart(user);

            /*
             * Nie można wykonać checkoutu pustego koszyka.
             *
             * To podstawowa walidacja biznesowa.
             */
            if (cart.getItems().isEmpty()) {
                throw ApiException.badRequest("Cannot checkout empty cart");
            }

            /*
             * Tworzymy zamówienie w statusie PENDING_PAYMENT.
             *
             * OrderService powinien zapisać snapshot:
             * - produktów,
             * - wariantów,
             * - cen,
             * - adresów,
             * - metody dostawy.
             *
             * Snapshot jest ważny, bo dane produktu mogą się później zmienić.
             */
            CustomerOrder order = orders.createPendingOrder(
                    user,
                    cart,
                    request.shippingAddress(),
                    request.billingAddress(),
                    request.shippingMethod()
            );

            /*
             * Rezerwujemy inventory dla każdej pozycji koszyka.
             *
             * Rezerwacja oznacza:
             * - produkt nie jest jeszcze sprzedany,
             * - ale stock jest tymczasowo zablokowany dla tego zamówienia.
             *
             * Jeśli płatność się powiedzie, rezerwacja zostanie potwierdzona.
             * Jeśli płatność się nie powiedzie albo rezerwacja wygaśnie, stock zostanie zwolniony.
             */
            for (var item : cart.getItems()) {
                inventory.reserve(
                        order.getId(),
                        item.getVariant(),
                        item.getQuantity()
                );
            }

            /*
             * Tworzymy płatność dla zamówienia.
             *
             * Klucz płatności bazuje na Idempotency-Key checkoutu.
             * Dzięki temu retry checkoutu nie powinien utworzyć wielu płatności
             * dla tego samego logicznego requestu.
             */
            Payment payment = payments.createPayment(
                    order,
                    "checkout-" + idempotencyKey
            );

            /*
             * Koszyk zostaje oznaczony jako CHECKED_OUT.
             *
             * Od tego momentu nie powinien być dalej używany jako aktywny koszyk.
             * Kolejne dodanie produktu powinno utworzyć nowy aktywny koszyk.
             */
            cart.markCheckedOut();

            /*
             * Zapisujemy wynik idempotentnej operacji.
             *
             * Przy kolejnym requestcie z tym samym Idempotency-Key
             * system odczyta orderId/paymentId z rekordu i zwróci ten sam wynik.
             */
            idempotency.complete(
                    record,
                    order.getId(),
                    payment.getId()
            );

            /*
             * Metryka sukcesu checkoutu.
             *
             * Zwiększamy ją dopiero po utworzeniu zamówienia, rezerwacji stocku,
             * utworzeniu płatności i zapisaniu idempotencji.
             */
            metrics.checkoutSucceeded();

            return new CheckoutDtos.CheckoutResponse(
                    orders.toResponse(order),
                    payments.toResponse(payment)
            );
        } catch (RuntimeException ex) {
            /*
             * Jeśli coś pójdzie źle, oznaczamy rekord idempotencji jako FAILED.
             *
             * Przykłady błędów:
             * - pusty koszyk,
             * - brak stocku,
             * - błąd tworzenia zamówienia,
             * - błąd tworzenia płatności.
             *
             * Następnie rzucamy wyjątek dalej, żeby transakcja została wycofana
             * i żeby klient API dostał właściwy błąd.
             */
            idempotency.fail(record);
            throw ex;
        }
    }
}