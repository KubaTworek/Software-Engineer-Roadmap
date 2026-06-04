package pl.jakubtworek.marketplace.ordering.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.marketplace.ordering.application.CancelOrderUseCase;
import pl.jakubtworek.marketplace.ordering.application.OrderRepository;
import pl.jakubtworek.marketplace.ordering.application.PlaceOrderUseCase;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;

import java.util.List;
import java.util.UUID;

/**
 * Adapter HTTP dla modułu Ordering.
 *
 * Ten kontroler wystawia operacje związane ze składaniem, odczytem i anulowaniem zamówień.
 * Należy do warstwy API/infrastruktury, więc jego odpowiedzialnością jest:
 * - przyjęcie żądania HTTP,
 * - wykonanie podstawowej walidacji wejścia,
 * - przetłumaczenie DTO HTTP na komendę use case’a,
 * - zwrócenie odpowiedzi HTTP.
 *
 * Kontroler nie powinien zawierać reguł biznesowych. Reguły takie jak:
 * - kiedy można złożyć zamówienie,
 * - kiedy można je anulować,
 * - jak liczyć total,
 * - jakie statusy są dozwolone,
 * powinny znajdować się w domenie albo warstwie aplikacyjnej.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /**
     * Use case odpowiedzialny za złożenie zamówienia.
     *
     * Kontroler nie tworzy zamówienia bezpośrednio. Zamiast tego buduje komendę
     * i deleguje wykonanie operacji do warstwy aplikacyjnej.
     */
    private final PlaceOrderUseCase placeOrder;

    /**
     * Use case odpowiedzialny za anulowanie zamówienia.
     *
     * Reguły anulowania powinny być egzekwowane w domenie, np. w agregacie Order,
     * a nie w kontrolerze.
     */
    private final CancelOrderUseCase cancelOrder;

    /**
     * Repozytorium używane tutaj do prostego endpointu odczytowego.
     *
     * W bardziej dojrzałej wersji można byłoby zastąpić je dedykowanym query service
     * albo osobnym modelem odczytowym. Wtedy API nie korzystałoby bezpośrednio
     * z repozytorium agregatu.
     */
    private final OrderRepository repository;

    public OrderController(
            PlaceOrderUseCase placeOrder,
            CancelOrderUseCase cancelOrder,
            OrderRepository repository
    ) {
        this.placeOrder = placeOrder;
        this.cancelOrder = cancelOrder;
        this.repository = repository;
    }

    /**
     * Składa nowe zamówienie.
     *
     * Przepływ:
     * 1. Spring waliduje request body dzięki @Valid.
     * 2. Jeśli klient nie poda correlationId, kontroler generuje nowe UUID.
     * 3. Linie z DTO HTTP są mapowane na linie komendy use case’a.
     * 4. Use case tworzy zamówienie i publikuje zdarzenie domenowe/integracyjne.
     * 5. API zwraca HTTP 201 Created oraz identyfikator zamówienia.
     *
     * correlationId służy do śledzenia całego flow zamówienia przez moduły:
     * Ordering -> Payment -> Inventory -> Fulfillment/Notification.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse place(@Valid @RequestBody PlaceOrderRequest request) {
        UUID correlationId = request.correlationId() == null
                ? UUID.randomUUID()
                : request.correlationId();

        var lines = request.lines().stream()
                .map(line -> new PlaceOrderUseCase.Line(
                        line.productId(),
                        line.quantity(),
                        line.unitAmount(),
                        line.currency()
                ))
                .toList();

        var id = placeOrder.handle(
                new PlaceOrderUseCase.Command(
                        request.customerId(),
                        lines,
                        correlationId
                )
        );

        return new IdResponse(id.value());
    }

    /**
     * Zwraca szczegóły zamówienia po identyfikatorze.
     *
     * UUID z adresu URL jest mapowany na domenowy value object OrderId.
     *
     * Obecne ograniczenie:
     * - orElseThrow() bez dedykowanego wyjątku może dać nieczytelny błąd HTTP.
     *
     * Lepsze rozwiązanie:
     * - rzucić OrderNotFoundException,
     * - zmapować go na HTTP 404 Not Found w @ControllerAdvice.
     */
    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        var order = repository.findById(OrderId.of(id))
                .orElseThrow();

        return new OrderResponse(
                order.id().value(),
                order.customerId().value(),
                order.status().name(),
                order.total().amount().toPlainString(),
                order.total().currency().getCurrencyCode()
        );
    }

    /**
     * Anuluje zamówienie.
     *
     * correlationId jest pobierany z nagłówka X-Correlation-Id.
     * Jeśli klient go nie poda, generujemy nowy identyfikator korelacji.
     *
     * Uwaga:
     * - kontroler nie sprawdza, czy zamówienie można anulować,
     * - kontroler tylko przekazuje intencję do use case’a,
     * - reguła anulowania powinna być egzekwowana w agregacie Order.
     */
    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Correlation-Id", required = false) UUID correlationId
    ) {
        cancelOrder.handle(
                id,
                correlationId == null ? UUID.randomUUID() : correlationId
        );
    }

    /**
     * DTO żądania złożenia zamówienia.
     *
     * Ten typ należy do warstwy API. Nie powinien być przekazywany do domeny bezpośrednio.
     * Kontroler mapuje go na komendę PlaceOrderUseCase.Command.
     */
    public record PlaceOrderRequest(
            @NotNull UUID customerId,

            /**
             * Lista linii zamówienia.
             *
             * @NotEmpty wymusza, żeby zamówienie miało przynajmniej jedną pozycję.
             */
            @NotEmpty List<@Valid LineRequest> lines,

            /**
             * Opcjonalny correlationId przekazany przez klienta.
             *
             * Jeśli go brakuje, API wygeneruje nowy UUID.
             */
            UUID correlationId
    ) {
    }

    /**
     * DTO pojedynczej linii zamówienia w żądaniu HTTP.
     *
     * Zawiera produkt, ilość oraz cenę jednostkową w momencie składania zamówienia.
     * Cena jest częścią requestu w tym uproszczonym projekcie, ale produkcyjnie
     * zwykle powinna pochodzić z modułu Catalog albo pricingu, a nie od klienta.
     */
    public record LineRequest(
            @NotNull UUID productId,

            /**
             * Ilość zamawianego produktu.
             *
             * @Min(1) blokuje ilość równą 0 albo ujemną.
             */
            @Min(1) int quantity,

            /**
             * Kwota jednostkowa jako tekst.
             *
             * W prostym API przyjmujemy String, ale docelowo warto dodać silniejszą walidację,
             * np. pattern liczbowy albo dedykowany typ DTO.
             */
            @NotBlank String unitAmount,

            /**
             * Kod waluty, np. PLN, EUR, USD.
             */
            @NotBlank String currency
    ) {
    }

    /**
     * Prosta odpowiedź zawierająca identyfikator nowo utworzonego zasobu.
     */
    public record IdResponse(UUID id) {
    }

    /**
     * DTO odpowiedzi z podstawowymi informacjami o zamówieniu.
     *
     * Celowo nie zwracamy tutaj całego agregatu Order.
     * API ma własny kontrakt odpowiedzi, niezależny od wewnętrznego modelu domenowego.
     */
    public record OrderResponse(
            UUID id,
            UUID customerId,
            String status,
            String totalAmount,
            String currency
    ) {
    }
}