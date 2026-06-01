package pl.jakubtworek.marketplace.inventory.api;

import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.marketplace.inventory.application.AddStockUseCase;
import pl.jakubtworek.marketplace.inventory.application.StockRepository;

import java.util.UUID;

@RestController
@RequestMapping("/api/stock")
public class StockController {
    private final AddStockUseCase addStock;
    private final StockRepository repository;

    public StockController(AddStockUseCase addStock, StockRepository repository) {
        this.addStock = addStock;
        this.repository = repository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@RequestBody AddStockRequest request) {
        addStock.handle(request.productId(), request.quantity());
    }

    @GetMapping("/{productId}")
    public StockResponse get(@PathVariable UUID productId) {
        var item = repository.findByProductId(productId).orElseThrow();
        return new StockResponse(item.productId(), item.availableQuantity(), item.reservedQuantity());
    }

    public record AddStockRequest(@NotNull UUID productId, int quantity) {}
    public record StockResponse(UUID productId, int availableQuantity, int reservedQuantity) {}
}
