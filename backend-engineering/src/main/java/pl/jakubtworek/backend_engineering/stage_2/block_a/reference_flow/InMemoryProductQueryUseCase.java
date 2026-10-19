package pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow;

import org.springframework.stereotype.Service;

import java.util.Map;

/** Shared application port implementation used by REST-like, GraphQL and gRPC adapters. */
@Service
public class InMemoryProductQueryUseCase implements ProductQueryUseCase {

    private final Map<String, ProductSnapshot> products = Map.of(
            "p-1", new ProductSnapshot("p-1", "seller-1", "Java Backend Handbook", 25, 3),
            "p-2", new ProductSnapshot("p-2", "seller-2", "Mechanical Keyboard", 18, 7));

    @Override
    public ProductSnapshot find(String productId) {
        ProductSnapshot product = products.get(productId);
        if (product == null) {
            throw new IllegalArgumentException("unknown product: " + productId);
        }
        return product;
    }
}
