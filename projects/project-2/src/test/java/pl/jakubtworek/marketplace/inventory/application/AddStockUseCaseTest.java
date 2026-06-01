package pl.jakubtworek.marketplace.inventory.application;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.inventory.infrastructure.InMemoryStockRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AddStockUseCaseTest {

    @Test
    void shouldCreateStockItemWhenItDoesNotExist() {
        InMemoryStockRepository repository = new InMemoryStockRepository();
        AddStockUseCase useCase = new AddStockUseCase(repository);
        UUID productId = UUID.randomUUID();

        useCase.handle(productId, 10);

        var item = repository.findByProductId(productId).orElseThrow();
        assertThat(item.availableQuantity()).isEqualTo(10);
        assertThat(item.reservedQuantity()).isZero();
    }

    @Test
    void shouldIncreaseExistingStockItem() {
        InMemoryStockRepository repository = new InMemoryStockRepository();
        AddStockUseCase useCase = new AddStockUseCase(repository);
        UUID productId = UUID.randomUUID();

        useCase.handle(productId, 10);
        useCase.handle(productId, 5);

        var item = repository.findByProductId(productId).orElseThrow();
        assertThat(item.availableQuantity()).isEqualTo(15);
    }
}
