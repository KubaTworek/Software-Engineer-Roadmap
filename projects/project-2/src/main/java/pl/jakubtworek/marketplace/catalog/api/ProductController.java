package pl.jakubtworek.marketplace.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.marketplace.catalog.application.CreateProductUseCase;
import pl.jakubtworek.marketplace.catalog.application.ProductRepository;
import pl.jakubtworek.marketplace.catalog.domain.ProductId;

import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final CreateProductUseCase createProduct;
    private final ProductRepository repository;

    public ProductController(CreateProductUseCase createProduct, ProductRepository repository) {
        this.createProduct = createProduct;
        this.repository = repository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse create(@Valid @RequestBody CreateProductRequest request) {
        ProductId id = createProduct.handle(new CreateProductUseCase.Command(request.name(), request.amount(), request.currency()));
        return new IdResponse(id.value());
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        var product = repository.findById(ProductId.of(id)).orElseThrow();
        return new ProductResponse(product.id().value(), product.name(), product.price().amount().toPlainString(), product.price().currency().getCurrencyCode(), product.status().name());
    }

    public record CreateProductRequest(@NotBlank String name, @NotBlank String amount, @NotBlank String currency) {}
    public record IdResponse(UUID id) {}
    public record ProductResponse(UUID id, String name, String amount, String currency, String status) {}
}
