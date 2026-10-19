package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Fail-closed policy fed by real SBOM, signature, provenance and scanner outputs. */
public final class ReleaseSecurityGate {

    private static final Pattern DIGEST_PINNED = Pattern.compile("^[^@]+@sha256:[0-9a-f]{64}$");
    private final int maximumHighVulnerabilities;

    public ReleaseSecurityGate(int maximumHighVulnerabilities) {
        if (maximumHighVulnerabilities < 0) throw new IllegalArgumentException("maximumHighVulnerabilities must be non-negative");
        this.maximumHighVulnerabilities = maximumHighVulnerabilities;
    }

    public Decision evaluate(ReleaseArtifact artifact) {
        if (artifact == null) throw new IllegalArgumentException("artifact is required");
        List<String> violations = new ArrayList<>();
        if (!DIGEST_PINNED.matcher(artifact.imageReference()).matches()) violations.add("image is not pinned by SHA-256 digest");
        if (!artifact.sbomGenerated()) violations.add("SBOM is missing");
        if (!artifact.signatureVerified()) violations.add("artifact signature is not verified");
        if (!artifact.provenanceVerified()) violations.add("build provenance is not verified");
        if (artifact.criticalVulnerabilities() > 0) violations.add("critical vulnerabilities are present");
        if (artifact.highVulnerabilities() > maximumHighVulnerabilities) violations.add("high vulnerability budget is exceeded");
        if (artifact.secretDetected()) violations.add("secret scanner found credential material");
        return new Decision(violations.isEmpty(), violations);
    }

    public record Decision(boolean approved, List<String> violations) {
        public Decision {
            violations = List.copyOf(violations);
        }
    }
}
