package pl.jakubtworek.marketplace.api;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.api.graphql.QueryComplexityBudget;
import pl.jakubtworek.marketplace.api.grpc.DeadlineBudget;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolSemanticsTest {
    @Test void graphqlCollectionMultiplierRejectsCheapLookingNestedQuery() {
        var query = new QueryComplexityBudget.Field(1, 100,
                List.of(new QueryComplexityBudget.Field(2, 50, List.of())));
        assertThatThrownBy(() -> new QueryComplexityBudget(1_000).verify(query))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void grpcDownstreamReceivesOnlyRemainingDeadline() {
        var child = new DeadlineBudget(Duration.ofMillis(500)).forDownstream(Duration.ofMillis(75));
        assertThat(child.remaining()).isEqualTo(Duration.ofMillis(425));
    }
}
