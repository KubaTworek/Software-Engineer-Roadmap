package pl.jakubtworek.cloudarchitecture.operations.recovery;

public record ReleaseChange(
        String imageDigest,
        String previousImageDigest,
        String schemaVersion,
        MigrationKind migrationKind,
        boolean previousApplicationCompatibleWithCurrentSchema) {
}
