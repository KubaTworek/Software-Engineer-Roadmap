package pl.jakubtworek.backend_engineering.stage_1.block_d.sql.connection_pool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectionPoolBudgetTest {

    @Test
    void shouldSplitTheGlobalBudgetAcrossInstances() {
        ConnectionPoolBudget budget = new ConnectionPoolBudget(200, 20, 6, 80);

        assertThat(budget.applicationConnectionBudget()).isEqualTo(144);
        assertThat(budget.maxPoolSizePerInstance()).isEqualTo(24);
        assertThat(budget.undistributedConnections()).isZero();
    }

    @Test
    void shouldMakeTheAutoscalingTradeOffVisible() {
        ConnectionPoolBudget sixInstances = new ConnectionPoolBudget(200, 20, 6, 80);
        ConnectionPoolBudget twelveInstances = new ConnectionPoolBudget(200, 20, 12, 80);

        assertThat(sixInstances.maxPoolSizePerInstance()).isEqualTo(24);
        assertThat(twelveInstances.maxPoolSizePerInstance()).isEqualTo(12);
    }

    @Test
    void shouldEstimateConcurrentDatabaseWorkUsingLittlesLaw() {
        assertThat(ConnectionPoolBudget.estimatedConcurrentDatabaseWork(400, 25))
                .isEqualTo(10);
        assertThat(ConnectionPoolBudget.estimatedConcurrentDatabaseWork(1, 1))
                .isEqualTo(1);
    }

    @Test
    void shouldRejectAnInvalidBudget() {
        assertThatThrownBy(() -> new ConnectionPoolBudget(20, 20, 2, 80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reservedConnections");
        assertThatThrownBy(() -> ConnectionPoolBudget.estimatedConcurrentDatabaseWork(
                Double.NaN,
                10
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
