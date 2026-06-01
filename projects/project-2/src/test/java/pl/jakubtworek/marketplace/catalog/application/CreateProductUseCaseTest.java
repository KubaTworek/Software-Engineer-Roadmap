package pl.jakubtworek.marketplace.catalog.application;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.catalog.domain.ProductStatus;
import pl.jakubtworek.marketplace.catalog.infrastructure.InMemoryProductRepository;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import static org.assertj.core.api.Assertions.assertThat;

class CreateProductUseCaseTest {

    @Test
    void shouldCreateAndPersistProduct() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        CreateProductUseCase useCase = new CreateProductUseCase(repository);

        var productId = useCase.handle(new CreateProductUseCase.Command("Keyboard", "199.99", "PLN"));

        var product = repository.findById(productId).orElseThrow();
        assertThat(product.name()).isEqualTo("Keyboard");
        assertThat(product.price()).isEqualTo(Money.of("199.99", "PLN"));
        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
    }
}
