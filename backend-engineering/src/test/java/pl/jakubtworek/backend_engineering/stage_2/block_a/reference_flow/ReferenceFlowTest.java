package pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_1.block_d.search_engine.SearchHit;
import pl.jakubtworek.backend_engineering.stage_1.block_d.search_engine.VersionedSearchIndex;
import pl.jakubtworek.backend_engineering.stage_2.block_a.graphql.ProductGraphQlController;
import pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.adapter.VersionedProductSearchProjectionAdapter;
import pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.application.CatalogProjectionRelay;
import pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.application.ProductChangedMessage;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceFlowTest {

    @Test
    void deliveryAdapterUsesTheCanonicalQueryUseCase() {
        InMemoryProductQueryUseCase useCase = new InMemoryProductQueryUseCase();

        ProductSnapshot throughGraphQlAdapter = new ProductGraphQlController(useCase).product("p-1");
        ProductSnapshot directlyFromApplicationPort = useCase.find("p-1");

        assertThat(throughGraphQlAdapter).isEqualTo(directlyFromApplicationPort);
    }

    @Test
    void outboxMessageUpdatesSearchAndLiveChannelExactlyOncePerVersion() {
        VersionedSearchIndex index = new VersionedSearchIndex();
        List<String> liveMessages = new ArrayList<>();
        CatalogProjectionRelay relay = new CatalogProjectionRelay(
                new VersionedProductSearchProjectionAdapter(index), liveMessages::add);
        ProductChangedMessage message = new ProductChangedMessage("evt-1", "p-1", 4, "Java Systems");

        assertThat(relay.relay(message)).isTrue();
        assertThat(relay.relay(message)).isFalse();

        assertThat(index.search("java", null, 10)).extracting(SearchHit::id).containsExactly("p-1");
        assertThat(liveMessages).containsExactly("PRODUCT_CHANGED:p-1:4");
    }
}
