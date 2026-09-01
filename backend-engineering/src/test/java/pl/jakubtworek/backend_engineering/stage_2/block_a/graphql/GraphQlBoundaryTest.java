package pl.jakubtworek.backend_engineering.stage_2.block_a.graphql;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphQlBoundaryTest {

    @Test
    void expensiveQueryIsRejectedBeforeResolverExecution() {
        QueryField query = QueryField.node("orders", 2,
                QueryField.node("lines", 3,
                        QueryField.node("product", 4, QueryField.leaf("reviews", 5))));

        assertThatThrownBy(() -> new QueryComplexityGuard(3, 20).inspect(List.of(query)))
                .isInstanceOf(QueryRejectedException.class)
                .hasMessageContaining("depth 4");
    }

    @Test
    void batchingAvoidsOneCallPerParentAndPreservesOrder() {
        CountingCatalog naiveCatalog = new CountingCatalog();
        List<String> ids = List.of("p-2", "p-1", "p-2");
        new NaiveProductResolver(naiveCatalog).products(ids);

        CountingCatalog batchingCatalog = new CountingCatalog();
        List<ProductView> products = new BatchingProductResolver(batchingCatalog).products(ids);

        assertThat(naiveCatalog.singleCalls).isEqualTo(3);
        assertThat(batchingCatalog.batchCalls).isEqualTo(1);
        assertThat(batchingCatalog.lastBatch).containsExactly("p-2", "p-1");
        assertThat(products).extracting(ProductView::id).containsExactlyElementsOf(ids);
    }

    @Test
    void sensitiveFieldChecksResourceOwnership() {
        ProductView product = new ProductView("p-1", "owner", "Book", 40);
        ProductFieldAuthorization authorization = new ProductFieldAuthorization();

        assertThat(authorization.internalMargin(product,
                new ProductFieldAuthorization.Principal("owner", Set.of()))).isEqualTo(40);
        assertThatThrownBy(() -> authorization.internalMargin(product,
                new ProductFieldAuthorization.Principal("other", Set.of("USER"))))
                .isInstanceOf(FieldAccessDeniedException.class);
    }

    @Test
    void listCardinalityContributesToQueryBudget() {
        QueryField query = QueryField.list("orders", 1, 100,
                QueryField.node("lines", 2, QueryField.leaf("product", 3)));

        assertThatThrownBy(() -> new QueryComplexityGuard(5, 400).inspect(List.of(query)))
                .isInstanceOf(QueryRejectedException.class)
                .hasMessageContaining("cost 501");
    }

    @Test
    void arithmeticOverflowIsRejectedFailClosed() {
        QueryField query = QueryField.list("orders", 1, Integer.MAX_VALUE,
                QueryField.list("lines", 1, Integer.MAX_VALUE, QueryField.leaf("id", 1)));

        assertThatThrownBy(() -> new QueryComplexityGuard(5, Integer.MAX_VALUE).inspect(List.of(query)))
                .isInstanceOf(QueryRejectedException.class)
                .hasMessageContaining("supported range");
    }

    private static final class CountingCatalog implements ProductCatalog {
        private final Map<String, ProductView> data = Map.of(
                "p-1", new ProductView("p-1", "u-1", "Book", 20),
                "p-2", new ProductView("p-2", "u-2", "Keyboard", 30));
        private int singleCalls;
        private int batchCalls;
        private List<String> lastBatch = List.of();

        @Override
        public ProductView loadOne(String id) {
            singleCalls++;
            return data.get(id);
        }

        @Override
        public Map<String, ProductView> loadBatch(List<String> ids) {
            batchCalls++;
            lastBatch = List.copyOf(ids);
            Map<String, ProductView> result = new LinkedHashMap<>();
            ids.forEach(id -> result.put(id, data.get(id)));
            return result;
        }
    }
}
