package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.service;

import pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.dto.ProductDto;
import pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.entity.ProductEntity;
import pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        this.productRepository = productRepository;
        this.productCacheService = productCacheService;
    }

    /**
     * Implements the cache-aside read path.
     *
     * Cache hit: return cached data immediately.
     * Cache miss: read from database, then store the result in cache.
     */
    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        return productCacheService.get(id).orElseGet(() -> {
            ProductEntity entity = productRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
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
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        entity.update(request.name(), request.price());
        ProductDto updated = toDto(entity);
        productCacheService.evict(id);
        return updated;
    }

    private ProductDto toDto(ProductEntity entity) {
        return new ProductDto(entity.getId(), entity.getName(), entity.getPrice());
    }
}
