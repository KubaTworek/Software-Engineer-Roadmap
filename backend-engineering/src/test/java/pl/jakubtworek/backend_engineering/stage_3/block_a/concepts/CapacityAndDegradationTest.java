package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.capacity.BottleneckAnalyzer;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.capacity.BottleneckType;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.capacity.CapacityCalculator;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.capacity.CapacityPlan;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.degradation.EmergencyLever;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.degradation.EmergencyLeverRegistry;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.degradation.ProductPage;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.degradation.ProductPageService;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapacityAndDegradationTest {

    @Test
    void calculatesLimitsAndSelectsTheSmallestBottleneck() {
        CapacityPlan plan = new CapacityPlan(
                2, 2, 0.75, 0.01,
                20, 0.5, 0.2,
                100, 0.25, 2
        );

        assertThat(CapacityCalculator.apiCpuLimitRps(plan)).isEqualTo(300);
        assertThat(CapacityCalculator.dependencyPoolLimitRps(plan)).isEqualTo(200);
        assertThat(CapacityCalculator.dbWriteLimitRps(plan)).isEqualTo(200);
        assertThat(new BottleneckAnalyzer().first(plan).type()).isEqualTo(BottleneckType.DEPENDENCY_POOL);
    }

    @Test
    void rejectsNonFiniteCapacityInputs() {
        assertThatThrownBy(() -> CapacityCalculator.concurrency(Double.NaN, 0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapacityPlan(
                1, Double.POSITIVE_INFINITY, 0.8, 0.01,
                10, 1, 0.1,
                100, 0.5, 1
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unusedResourcesDoNotBecomeArtificialBottlenecks() {
        CapacityPlan readOnlyPlanWithoutRemoteDependency = new CapacityPlan(
                1, 2, 0.5, 0.01,
                10, 0, 0.1,
                100, 0, 0
        );

        assertThat(CapacityCalculator.dependencyPoolLimitRps(readOnlyPlanWithoutRemoteDependency))
                .isPositive()
                .isInfinite();
        assertThat(CapacityCalculator.dbWriteLimitRps(readOnlyPlanWithoutRemoteDependency))
                .isPositive()
                .isInfinite();
        assertThat(new BottleneckAnalyzer().first(readOnlyPlanWithoutRemoteDependency).type())
                .isEqualTo(BottleneckType.API_CPU);
    }

    @Test
    void recommendationFailureDegradesOnlyTheOptionalPart() {
        ProductPageService service = new ProductPageService(
                new EmergencyLeverRegistry(),
                productId -> { throw new IllegalStateException("dependency unavailable"); }
        );

        ProductPage page = service.getProductPage("p-1");

        assertThat(page.productId()).isEqualTo("p-1");
        assertThat(page.recommendations()).isEmpty();
        assertThat(page.degraded()).isTrue();
    }

    @Test
    void emergencyLeverSkipsTheDependencyAndSnapshotsAreDefensive() {
        EmergencyLeverRegistry levers = new EmergencyLeverRegistry();
        levers.enable(EmergencyLever.DISABLE_RECOMMENDATIONS);
        ProductPageService service = new ProductPageService(
                levers,
                productId -> { throw new AssertionError("client must not be called"); }
        );

        assertThat(service.getProductPage("p-1").degraded()).isTrue();
        assertThat(levers.enabledLevers()).containsExactly(EmergencyLever.DISABLE_RECOMMENDATIONS);

        List<String> mutable = new ArrayList<>(List.of("p-2"));
        ProductPage page = new ProductPage("p-1", "name", "description", mutable, false);
        mutable.clear();
        assertThat(page.recommendations()).containsExactly("p-2");
    }
}
