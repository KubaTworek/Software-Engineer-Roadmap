package pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.adapter;

import pl.jakubtworek.backend_engineering.stage_1.block_d.search_engine.SearchDocument;
import pl.jakubtworek.backend_engineering.stage_1.block_d.search_engine.VersionedSearchIndex;
import pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.application.ProductChangedMessage;
import pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.application.ProductSearchProjection;

public final class VersionedProductSearchProjectionAdapter implements ProductSearchProjection {

    private final VersionedSearchIndex index;

    public VersionedProductSearchProjectionAdapter(VersionedSearchIndex index) {
        this.index = index;
    }

    @Override
    public boolean apply(ProductChangedMessage message) {
        return index.apply(SearchDocument.active(
                message.productId(), message.version(), message.name(), "catalog product"));
    }
}
