package pl.jakubtworek.backend_engineering.stage_2.block_a.graphql;

import java.util.List;
import java.util.Map;

/** DataLoader-like boundary: collect keys, load once, restore request order. */
public final class BatchingProductResolver {

    private final ProductCatalog catalog;

    public BatchingProductResolver(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    public List<ProductView> products(List<String> ids) {
        Map<String, ProductView> loaded = catalog.loadBatch(ids.stream().distinct().toList());
        return ids.stream().map(id -> {
            ProductView product = loaded.get(id);
            if (product == null) {
                throw new IllegalArgumentException("unknown product: " + id);
            }
            return product;
        }).toList();
    }
}
