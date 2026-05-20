package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.springframework.web.bind.annotation.*;

/**
 * Controller delegates authorization to service layer.
 *
 * Keeping @PreAuthorize on services is usually better,
 * because business rules stay close to business operations.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    @PutMapping("/{id}")
    public Order updateOrder(
            @PathVariable Long id,
            @RequestBody String description
    ) {
        return orderService.updateOrder(id, description);
    }

    @DeleteMapping("/{id}")
    public void deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
    }
}