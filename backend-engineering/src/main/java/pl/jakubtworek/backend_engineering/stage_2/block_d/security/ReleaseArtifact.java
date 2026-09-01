package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

public record ReleaseArtifact(
        String imageReference,
        boolean sbomGenerated,
        boolean signatureVerified,
        boolean provenanceVerified,
        int criticalVulnerabilities,
        int highVulnerabilities,
        boolean secretDetected) {

    public ReleaseArtifact {
        if (imageReference == null || imageReference.isBlank()) throw new IllegalArgumentException("imageReference is required");
        if (criticalVulnerabilities < 0 || highVulnerabilities < 0) {
            throw new IllegalArgumentException("vulnerability counts must be non-negative");
        }
    }
}
