package pl.jakubtworek.marketplace.catalog.infrastructure;

import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.catalog.application.ProductRepository;
import pl.jakubtworek.marketplace.catalog.domain.Product;
import pl.jakubtworek.marketplace.catalog.domain.ProductId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryProductRepository implements ProductRepository {
    private final Map<ProductId, Product> products = new ConcurrentHashMap<>();

    @Override
    public Product save(Product product) {
        products.put(product.id(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        return Optional.ofNullable(products.get(id));
    }
}
