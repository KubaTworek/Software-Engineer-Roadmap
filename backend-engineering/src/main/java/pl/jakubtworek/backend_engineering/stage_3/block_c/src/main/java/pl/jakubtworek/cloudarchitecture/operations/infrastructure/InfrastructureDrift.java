package pl.jakubtworek.cloudarchitecture.operations.infrastructure;

public record InfrastructureDrift(
        DriftType type,
        String resourceAddress,
        String attribute,
        String expected,
        String actual) {
}
