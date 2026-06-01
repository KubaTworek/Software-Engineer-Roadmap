package pl.jakubtworek.marketplace.ordering.api;

import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final PlaceOrderUseCase placeOrder;
    private final CancelOrderUseCase cancelOrder;
    private final OrderRepository repository;

    public OrderController(PlaceOrderUseCase placeOrder, CancelOrderUseCase cancelOrder, OrderRepository repository) {
        this.placeOrder = placeOrder;
        this.cancelOrder = cancelOrder;
        this.repository = repository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse place(@Valid @RequestBody PlaceOrderRequest request) {
        UUID correlationId = request.correlationId() == null ? UUID.randomUUID() : request.correlationId();
        var lines = request.lines().stream()
                .map(line -> new PlaceOrderUseCase.Line(line.productId(), line.quantity(), line.unitAmount(), line.currency()))
                .toList();
        var id = placeOrder.handle(new PlaceOrderUseCase.Command(request.customerId(), lines, correlationId));
        return new IdResponse(id.value());
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        var order = repository.findById(OrderId.of(id)).orElseThrow();
        return new OrderResponse(order.id().value(), order.customerId().value(), order.status().name(), order.total().amount().toPlainString(), order.total().currency().getCurrencyCode());
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id, @RequestHeader(value = "X-Correlation-Id", required = false) UUID correlationId) {
        cancelOrder.handle(id, correlationId == null ? UUID.randomUUID() : correlationId);
    }

    public record PlaceOrderRequest(@NotNull UUID customerId, @NotEmpty List<LineRequest> lines, UUID correlationId) {}
    public record LineRequest(@NotNull UUID productId, int quantity, String unitAmount, String currency) {}
    public record IdResponse(UUID id) {}
    public record OrderResponse(UUID id, UUID customerId, String status, String totalAmount, String currency) {}
}
