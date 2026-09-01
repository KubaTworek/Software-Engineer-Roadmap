package pl.jakubtworek.backend_engineering.stage_2.block_a.graphql;

import java.util.List;

/** Deliberate N+1 counterexample: one downstream call for every parent row. */
public final class NaiveProductResolver {

    private final ProductCatalog catalog;

    public NaiveProductResolver(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    public List<ProductView> products(List<String> ids) {
        return ids.stream().map(catalog::loadOne).toList();
    }
}
