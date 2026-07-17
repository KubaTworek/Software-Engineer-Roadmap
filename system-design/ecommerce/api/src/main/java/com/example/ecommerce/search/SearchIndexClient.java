package com.example.ecommerce.search;
import java.util.List;
public interface SearchIndexClient { void indexProduct(ProductSearchDocument document); List<Long> searchProductIds(String query); }
