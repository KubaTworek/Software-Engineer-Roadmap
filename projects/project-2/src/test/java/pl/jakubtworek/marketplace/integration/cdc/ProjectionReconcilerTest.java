package pl.jakubtworek.marketplace.integration.cdc;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ProjectionReconcilerTest {
    @Test
    void repairsMissingAndDivergentRowsWithoutReplayingBusinessCommands() {
        Map<String, String> source = Map.of("o1", "CONFIRMED", "o2", "CANCELLED");
        Map<String, String> projection = new HashMap<>(Map.of("o1", "PENDING"));
        var report = new ProjectionReconciler<String, String>()
                .reconcile(source, projection, projection::put);
        assertThat(report.missing()).containsExactly("o2");
        assertThat(report.divergent()).containsExactly("o1");
        assertThat(projection).containsAllEntriesOf(source);
    }
}
