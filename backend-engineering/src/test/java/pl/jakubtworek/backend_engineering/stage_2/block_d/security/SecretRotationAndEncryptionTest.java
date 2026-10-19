package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SecretRotationAndEncryptionTest {

    @Test
    void rotationAcceptsBothVersionsOnlyDuringExplicitGracePeriod() {
        char[] oldSecret = "old-secret-value-123".toCharArray();
        char[] newSecret = "new-secret-value-456".toCharArray();

        try (RotatingSecret secret = new RotatingSecret("v1", oldSecret)) {
            secret.rotate("v2", newSecret);

            assertThat(secret.activeVersion()).isEqualTo("v2");
            assertThat(secret.gracePeriodActive()).isTrue();
            assertThat(secret.matches(oldSecret)).isTrue();
            assertThat(secret.matches(newSecret)).isTrue();
            assertThat(secret.toString()).doesNotContain("old-secret", "new-secret");

            secret.retirePrevious();
            assertThat(secret.matches(oldSecret)).isFalse();
            assertThat(secret.matches(newSecret)).isTrue();
        }
    }

    @Test
    void everySensitiveHopAndCopyMustNameEncryptionOwner() {
        DataProtectionBoundary boundary = new DataProtectionBoundary(
                "customer-profile",
                SecurityDataFlow.DataSensitivity.PII,
                List.of(
                        new DataProtectionBoundary.TransportHop("browser -> load-balancer", true, "platform-team"),
                        new DataProtectionBoundary.TransportHop("load-balancer -> application", false, "")),
                List.of(
                        new DataProtectionBoundary.StorageCopy("primary-db", true, "security-team"),
                        new DataProtectionBoundary.StorageCopy("analytics-export", false, null)));

        assertThat(new DataProtectionValidator().violations(boundary)).containsExactly(
                "unencrypted transport hop: load-balancer -> application",
                "transport certificate owner missing: load-balancer -> application",
                "unencrypted storage copy: analytics-export",
                "storage key owner missing: analytics-export");
    }
}
