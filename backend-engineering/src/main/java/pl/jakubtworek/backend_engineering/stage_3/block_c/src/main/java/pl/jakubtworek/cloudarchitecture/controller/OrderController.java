package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.controller;

import pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.dto.CreateOrderRequest;
import pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.dto.OrderCreatedResponse;
import pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.service.IdempotencyService;
import pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.service.OrderService;
import pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.service.RateLimiterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller responsible for write operations.
 *
 * Write operations are protected with idempotency keys and rate limiting,
 * because retries and traffic spikes are normal in distributed cloud systems.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final RateLimiterService rateLimiterService;

    public OrderController(OrderService orderService, IdempotencyService idempotencyService, RateLimiterService rateLimiterService) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Creates an order in an idempotent way.
     *
     * The Idempotency-Key header prevents duplicate orders when a client retries
     * the same request after a timeout or network error.
     */
    @PostMapping
    public ResponseEntity<OrderCreatedResponse> createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "User-Id", defaultValue = "anonymous") String userId,
            @RequestBody CreateOrderRequest request
    ) {
        rateLimiterService.checkLimit(userId);
        OrderCreatedResponse response = idempotencyService.executeOnce(
                idempotencyKey,
                OrderCreatedResponse.class,
                () -> orderService.createOrder(request)
        );
        return ResponseEntity.status(201).body(response);
    }
}
