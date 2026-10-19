package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantCacheAndMetricsTest {

    @Test
    void cacheKeyMustIncludeTenantEvenWhenResourceIdIsIdentical() {
        TenantCacheKey alpha = new TenantCacheKey("subject", new TenantId("alpha-co"), "customer-1");
        TenantCacheKey beta = new TenantCacheKey("subject", new TenantId("beta-co"), "customer-1");

        assertThat(alpha.serialized()).isEqualTo("subject:tenant:alpha-co:resource:customer-1");
        assertThat(alpha).isNotEqualTo(beta);
        assertThat(alpha.serialized()).isNotEqualTo(beta.serialized());
    }

    @Test
    void metricPolicyMustKeepTenantAwarenessWithoutUnboundedTenantLabel() {
        TenantMetricPolicy policy = new TenantMetricPolicy(16);
        Set<String> buckets = new HashSet<>();

        for (int index = 0; index < 1_000; index++) {
            TenantId tenantId = new TenantId("tenant-" + index);
            Map<String, String> tags = policy.tags(tenantId, TenantMetricPolicy.Plan.STANDARD, "success");
            buckets.add(tags.get("tenant_bucket"));

            assertThat(tags).doesNotContainKey("tenant_id");
            assertThat(tags.values()).doesNotContain(tenantId.value());
        }

        assertThat(buckets).hasSizeLessThanOrEqualTo(16);
    }
}
