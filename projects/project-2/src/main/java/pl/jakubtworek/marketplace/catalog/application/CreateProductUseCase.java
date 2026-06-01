package pl.jakubtworek.marketplace.catalog.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.catalog.domain.Product;
import pl.jakubtworek.marketplace.catalog.domain.ProductId;
import pl.jakubtworek.marketplace.shared.kernel.Money;

@Service
public class CreateProductUseCase {
    private final ProductRepository repository;

    public CreateProductUseCase(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ProductId handle(Command command) {
        Product product = Product.create(command.name(), Money.of(command.amount(), command.currency()));
        return repository.save(product).id();
    }

    public record Command(String name, String amount, String currency) {}
}
