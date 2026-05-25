package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.controller;

import pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.dto.ProductDto;
import pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP controller for product operations.
 *
 * The controller does not store session data or request state locally.
 * Every request can be handled by any running instance of the service.
 */
@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductService productService;

    /**
     * Dependencies are injected through the constructor to keep the class testable
     * and to avoid hidden global state.
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Reads a product using the cache-aside pattern.
     *
     * First the service tries Redis/Memorystore. If the cache does not contain
     * the value, the service reads from Cloud SQL and then stores the result
     * in cache for future requests.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    /**
     * Updates a product and invalidates the related cache entry.
     *
     * Cache invalidation is required because otherwise users may receive stale
     * product data after a successful database update.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable Long id, @RequestBody ProductDto request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }
}
