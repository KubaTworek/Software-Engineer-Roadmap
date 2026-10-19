package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TenantCapacityIsolationTest {

    @Test
    void noisyTenantMustNotConsumeAnotherTenantsConcurrencyPermit() {
        TenantId noisy = new TenantId("noisy-tenant");
        TenantId quiet = new TenantId("quiet-tenant");
        TenantConcurrencyQuota quota = new TenantConcurrencyQuota(1);

        try (TenantConcurrencyQuota.Permit ignored = quota.tryAcquire(noisy)) {
            assertThatThrownBy(() -> quota.tryAcquire(noisy))
                    .isInstanceOf(TenantConcurrencyQuota.TenantQuotaExceededException.class);

            try (TenantConcurrencyQuota.Permit quietPermit = quota.tryAcquire(quiet)) {
                assertThat(quota.snapshot(quiet).active()).isOne();
            }
        }

        assertThat(quota.snapshot(noisy))
                .isEqualTo(new TenantConcurrencyQuota.Snapshot(0, 1, 1));
        assertThat(quota.snapshot(quiet))
                .isEqualTo(new TenantConcurrencyQuota.Snapshot(0, 1, 0));
    }

    @Test
    void closingPermitTwiceMustNotIncreaseCapacity() {
        TenantId tenant = new TenantId("alpha-co");
        TenantConcurrencyQuota quota = new TenantConcurrencyQuota(1);
        TenantConcurrencyQuota.Permit permit = quota.tryAcquire(tenant);

        permit.close();
        permit.close();

        try (TenantConcurrencyQuota.Permit ignored = quota.tryAcquire(tenant)) {
            assertThatThrownBy(() -> quota.tryAcquire(tenant))
                    .isInstanceOf(TenantConcurrencyQuota.TenantQuotaExceededException.class);
        }
    }
}
