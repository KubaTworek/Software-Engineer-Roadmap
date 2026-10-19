package pl.jakubtworek.cloudarchitecture.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pl.jakubtworek.cloudarchitecture.dto.ProductDto;
import pl.jakubtworek.cloudarchitecture.entity.ProductEntity;
import pl.jakubtworek.cloudarchitecture.repository.ProductRepository;
import pl.jakubtworek.cloudarchitecture.service.ProductCacheService;
import pl.jakubtworek.cloudarchitecture.service.ProductService;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void returnsCacheHitWithoutQueryingTheDatabase() {
        ProductRepository repository = mock(ProductRepository.class);
        ProductCacheService cache = mock(ProductCacheService.class);
        ProductDto cached = new ProductDto(12L, "Keyboard", new BigDecimal("199.99"));
        when(cache.get(12L)).thenReturn(Optional.of(cached));

        ProductDto result = new ProductService(repository, cache).getProductById(12L);

        assertThat(result).isEqualTo(cached);
        verify(repository, never()).findById(12L);
    }

    @Test
    void invalidatesCacheOnlyAfterACommittedUpdate() {
        ProductRepository repository = mock(ProductRepository.class);
        ProductCacheService cache = mock(ProductCacheService.class);
        ProductEntity entity = new ProductEntity("Keyboard", new BigDecimal("199.99"));
        when(repository.findById(12L)).thenReturn(Optional.of(entity));
        TransactionSynchronizationManager.initSynchronization();

        new ProductService(repository, cache).updateProduct(
                12L,
                new ProductDto(12L, "Mechanical keyboard", new BigDecimal("249.99"))
        );

        verify(cache, never()).evict(12L);
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(cache).evict(12L);
    }
}
