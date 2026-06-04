package pl.jakubtworek.backend.order.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.backend.order.application.OrderService;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    OrderResponse create(@Valid @RequestBody CreateOrderRequest request,
                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return service.create(request, idempotencyKey);
    }

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
