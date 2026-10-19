package pl.jakubtworek.cloudarchitecture.operations.infrastructure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InfrastructureDriftDetector {

    public List<InfrastructureDrift> detect(
            Collection<InfrastructureResource> desired,
            Collection<InfrastructureResource> actual) {
        Map<String, InfrastructureResource> desiredByAddress = index(desired);
        Map<String, InfrastructureResource> actualByAddress = index(actual);
        List<InfrastructureDrift> drift = new ArrayList<>();

        desiredByAddress.forEach((address, expected) -> {
            InfrastructureResource observed = actualByAddress.get(address);
            if (observed == null) {
                drift.add(new InfrastructureDrift(
                        DriftType.MISSING_RESOURCE, address, null, expected.type(), null));
                return;
            }
            if (!expected.type().equals(observed.type())) {
                drift.add(new InfrastructureDrift(
                        DriftType.CHANGED_ATTRIBUTE, address, "$type", expected.type(), observed.type()));
            }
            expected.managedAttributes().forEach((attribute, expectedValue) -> {
                String actualValue = observed.managedAttributes().get(attribute);
                if (!expectedValue.equals(actualValue)) {
                    drift.add(new InfrastructureDrift(
                            DriftType.CHANGED_ATTRIBUTE, address, attribute, expectedValue, actualValue));
                }
            });
        });

        actualByAddress.keySet().stream()
                .filter(address -> !desiredByAddress.containsKey(address))
                .forEach(address -> drift.add(new InfrastructureDrift(
                        DriftType.UNEXPECTED_RESOURCE,
                        address,
                        null,
                        null,
                        actualByAddress.get(address).type())));

        return List.copyOf(drift);
    }

    private Map<String, InfrastructureResource> index(Collection<InfrastructureResource> resources) {
        return resources.stream().collect(Collectors.toMap(
                InfrastructureResource::address,
                Function.identity(),
                (first, duplicate) -> {
                    throw new IllegalArgumentException("Duplicate IaC address: " + first.address());
                }));
    }
}
