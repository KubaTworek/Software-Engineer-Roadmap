package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeatureFlagTest {

    @Test
    void assignmentIsStableForTheSameSubject() {
        FeatureFlag flag = new FeatureFlag("new-checkout", true, false, 25);

        assertThat(flag.enabledFor("tenant-17")).isEqualTo(flag.enabledFor("tenant-17"));
    }

    @Test
    void killSwitchOverridesAFullRollout() {
        FeatureFlag flag = new FeatureFlag("new-checkout", true, true, 100);

        assertThat(flag.enabledFor("tenant-17")).isFalse();
    }
}
