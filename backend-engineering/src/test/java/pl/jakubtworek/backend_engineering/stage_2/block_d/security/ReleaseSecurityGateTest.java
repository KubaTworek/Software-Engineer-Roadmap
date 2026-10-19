package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReleaseSecurityGateTest {

    private final ReleaseSecurityGate gate = new ReleaseSecurityGate(0);

    @Test
    void mutableUnsignedArtifactWithoutSbomMustFailClosed() {
        ReleaseArtifact artifact = new ReleaseArtifact(
                "registry.example.com/backend:latest", false, false, false, 1, 2, true);

        ReleaseSecurityGate.Decision decision = gate.evaluate(artifact);

        assertThat(decision.approved()).isFalse();
        assertThat(decision.violations()).containsExactly(
                "image is not pinned by SHA-256 digest",
                "SBOM is missing",
                "artifact signature is not verified",
                "build provenance is not verified",
                "critical vulnerabilities are present",
                "high vulnerability budget is exceeded",
                "secret scanner found credential material");
    }

    @Test
    void pinnedScannedSignedArtifactWithProvenanceIsApproved() {
        ReleaseArtifact artifact = new ReleaseArtifact(
                "registry.example.com/backend@sha256:" + "a".repeat(64),
                true, true, true, 0, 0, false);

        assertThat(gate.evaluate(artifact).approved()).isTrue();
    }
}
