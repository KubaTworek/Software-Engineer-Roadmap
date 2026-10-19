package com.example.searchindexer;

import com.example.searchindexer.catalog.InventoryItem;
import com.example.searchindexer.catalog.InventoryItemRepository;
import com.example.searchindexer.catalog.Product;
import com.example.searchindexer.catalog.ProductRepository;
import com.example.searchindexer.catalog.ProductVariant;
import com.example.searchindexer.outbox.OutboxEvent;
import com.example.searchindexer.outbox.OutboxEventRepository;
import com.example.searchindexer.outbox.ProcessedSearchEventRepository;
import com.example.searchindexer.search.OpenSearchClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SearchIndexWorkerTest {
    @Test
    void inventoryEventReindexesOwningProductBeforeRecordingMarker() {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        ProcessedSearchEventRepository processed = mock(ProcessedSearchEventRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        InventoryItemRepository inventory = mock(InventoryItemRepository.class);
        OpenSearchClient openSearch = mock(OpenSearchClient.class);

        OutboxEvent event = mock(OutboxEvent.class);
        when(event.getId()).thenReturn(7L);
        when(event.getAggregateType()).thenReturn("InventoryItem");
        when(event.getAggregateId()).thenReturn("11");
        when(outbox.findUnprocessedSearchEvents()).thenReturn(List.of(event));

        Product product = mock(Product.class);
        when(product.getId()).thenReturn(99L);
        when(product.getVariants()).thenReturn(List.of());
        ProductVariant variant = mock(ProductVariant.class);
        when(variant.getProduct()).thenReturn(product);
        InventoryItem item = mock(InventoryItem.class);
        when(item.getVariant()).thenReturn(variant);
        when(inventory.findByVariantId(11L)).thenReturn(Optional.of(item));
        when(products.findById(99L)).thenReturn(Optional.of(product));

        new SearchIndexWorker(
                outbox, processed, products, inventory, openSearch,
                10, new SimpleMeterRegistry()).process();

        verify(openSearch).indexProduct(any());
        verify(processed).save(any());
    }
}
