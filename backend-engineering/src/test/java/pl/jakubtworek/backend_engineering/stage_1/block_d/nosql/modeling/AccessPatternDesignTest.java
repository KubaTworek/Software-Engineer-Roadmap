package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.modeling;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccessPatternDesignTest {

    @Test
    void shouldSupportTheQueryTheTableWasDesignedFor() {
        AccessPatternDesign design = AccessPatternDesignCatalog.ordersByUserAndStatus();
        AccessPatternDesign.QueryShape query = new AccessPatternDesign.QueryShape(
                Set.of("userId", "status"),
                List.of("createdAt DESC"),
                Set.of("orderId", "totalAmount")
        );

        assertThat(design.evaluate(query).supported()).isTrue();
    }

    @Test
    void shouldExposeThatAQueryWithoutTheWholePartitionKeyNeedsAnotherProjection() {
        AccessPatternDesign design = AccessPatternDesignCatalog.ordersByUserAndStatus();
        AccessPatternDesign.QueryShape allStatusesForUser = new AccessPatternDesign.QueryShape(
                Set.of("userId"),
                List.of("createdAt DESC"),
                Set.of("orderId")
        );

        AccessPatternDesign.DesignEvaluation result = design.evaluate(allStatusesForUser);

        assertThat(result.supported()).isFalse();
        assertThat(result.violations()).anyMatch(message -> message.contains("status"));
    }

    @Test
    void shouldRejectAFieldThatWouldRequireAnUnplannedLookup() {
        AccessPatternDesign design = AccessPatternDesignCatalog.metricsByDeviceAndDay();
        AccessPatternDesign.QueryShape query = new AccessPatternDesign.QueryShape(
                Set.of("deviceId", "bucketDay"),
                List.of("metricTime DESC"),
                Set.of("metricTime", "firmwareVersion")
        );

        assertThat(design.evaluate(query).violations())
                .contains("fields require another lookup: [firmwareVersion]");
    }
}
