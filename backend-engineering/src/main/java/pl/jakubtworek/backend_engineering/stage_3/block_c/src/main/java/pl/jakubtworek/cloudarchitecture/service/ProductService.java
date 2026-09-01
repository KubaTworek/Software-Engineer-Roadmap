package pl.jakubtworek.cloudarchitecture.service;

import pl.jakubtworek.cloudarchitecture.dto.ProductDto;
import pl.jakubtworek.cloudarchitecture.entity.ProductEntity;
import pl.jakubtworek.cloudarchitecture.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/**
 * Application service implementing product use cases.
 *
 * This service combines Cloud SQL persistence with Redis/Memorystore caching.
 */
@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductCacheService productCacheService;

    public ProductService(ProductRepository productRepository, ProductCacheService productCacheService) {
        this.productRepository = Objects.requireNonNull(productRepository, "productRepository must not be null");
        this.productCacheService = Objects.requireNonNull(productCacheService, "productCacheService must not be null");
    }

    /**
     * Implements the cache-aside read path.
     *
     * Cache hit: return cached data immediately.
     * Cache miss: read from database, then store the result in cache.
     */
    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        Long productId = requirePositive(id, "id");
        return productCacheService.get(productId).orElseGet(() -> {
            ProductEntity entity = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
            ProductDto dto = toDto(entity);
            productCacheService.put(dto);
            return dto;
        });
    }

    /**
     * Updates the database first and invalidates cache afterwards.
     *
     * This prevents stale reads after a successful write operation.
     */
    @Transactional
    public ProductDto updateProduct(Long id, ProductDto request) {
        Long productId = requirePositive(id, "id");
        Objects.requireNonNull(request, "request must not be null");
        ProductEntity entity = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        entity.update(request.name(), request.price());
        ProductDto updated = toDto(entity);
        evictAfterCommit(productId);
        return updated;
    }

    private void evictAfterCommit(Long productId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            productCacheService.evict(productId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                productCacheService.evict(productId);
            }
        });
    }

    private static Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private ProductDto toDto(ProductEntity entity) {
        return new ProductDto(entity.getId(), entity.getName(), entity.getPrice());
    }
}
