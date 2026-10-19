package pl.jakubtworek.cloudarchitecture.operations.infrastructure;

import java.util.Map;

/** A small provider-independent projection of attributes owned by IaC. */
public record InfrastructureResource(String address, String type, Map<String, String> managedAttributes) {

    public InfrastructureResource {
        managedAttributes = Map.copyOf(managedAttributes);
    }
}
