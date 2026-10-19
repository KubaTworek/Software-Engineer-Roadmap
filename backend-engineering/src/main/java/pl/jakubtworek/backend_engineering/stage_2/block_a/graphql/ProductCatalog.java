package pl.jakubtworek.backend_engineering.stage_2.block_a.graphql;

import java.util.List;
import java.util.Map;

public interface ProductCatalog {
    ProductView loadOne(String id);

    Map<String, ProductView> loadBatch(List<String> ids);
}
