package pl.jakubtworek.marketplace.catalog.infrastructure;

import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.catalog.application.ProductRepository;
import pl.jakubtworek.marketplace.catalog.domain.ProductId;
import pl.jakubtworek.marketplace.catalog.domain.ProductStatus;
import pl.jakubtworek.marketplace.ordering.application.ProductPricingPort;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.NoSuchElementException;
import java.util.UUID;

/** Adapter translacyjny na granicy Catalog -> Ordering; nie przepuszcza agregatu. */
@Component
public class CatalogProductPricingAdapter implements ProductPricingPort {
    private final ProductRepository products;
    public CatalogProductPricingAdapter(ProductRepository products) { this.products = products; }

    @Override
    public Money currentPrice(UUID productId) {
        var product = products.findById(ProductId.of(productId))
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + productId));
        if (product.status() != ProductStatus.ACTIVE) throw new IllegalArgumentException("Product is inactive: " + productId);
        return product.price();
    }
}
