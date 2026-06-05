package pl.jakubtworek.backend.order.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.backend.order.application.OrderService;

import java.util.UUID;

/**
 * Kontroler HTTP dla Order Service.
 *
 * Odpowiada za wystawienie API związane z zamówieniami:
 *
 * - utworzenie zamówienia,
 * - pobranie zamówienia po ID.
 *
 * Kontroler nie powinien zawierać logiki biznesowej. Jego rola to:
 *
 * - przyjąć request HTTP,
 * - uruchomić walidację wejścia,
 * - przekazać dane do warstwy aplikacyjnej,
 * - zwrócić DTO odpowiedzi.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    /**
     * Warstwa aplikacyjna odpowiedzialna za właściwy proces tworzenia i pobierania zamówień.
     *
     * To w OrderService powinny znajdować się decyzje biznesowe, np.:
     *
     * - sprawdzenie rezerwacji,
     * - wywołanie payment-service,
     * - obsługa idempotency key,
     * - publikacja eventu order.paid,
     * - graceful degradation do PAYMENT_PENDING.
     */
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /**
     * Tworzy nowe zamówienie na podstawie istniejącej rezerwacji.
     *
     * Request body jest walidowane przez Bean Validation dzięki adnotacji @Valid.
     * Jeśli CreateOrderRequest ma np. @NotNull na reservationId albo userId,
     * Spring automatycznie zwróci 400 Bad Request dla niepoprawnego payloadu.
     *
     * Idempotency-Key jest opcjonalnym nagłówkiem, ale dla operacji typu POST /orders
     * jest bardzo istotny. Klient może ponowić request po timeoucie, a serwis powinien
     * rozpoznać, że to ta sama operacja, zamiast utworzyć drugie zamówienie.
     *
     * Przykład:
     *
     * POST /orders
     * Idempotency-Key: 3fa85f64-5717-4562-b3fc-2c963f66afa6
     *
     * {
     *   "reservationId": "...",
     *   "userId": "..."
     * }
     */
    @PostMapping
    OrderResponse create(@Valid @RequestBody CreateOrderRequest request,
                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return service.create(request, idempotencyKey);
    }

    /**
     * Pobiera zamówienie po jego identyfikatorze.
     *
     * UUID w ścieżce jest automatycznie konwertowany przez Springa.
     * Jeśli klient poda niepoprawny format UUID, Spring zwróci błąd 400 Bad Request.
     *
     * Przykład:
     *
     * GET /orders/11111111-1111-1111-1111-111111111111
     */
    @GetMapping("/{id}")
    OrderResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}