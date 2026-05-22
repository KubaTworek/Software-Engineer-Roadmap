package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.contracts;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.shared.integration.EventContractDescriptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// Registry of published contracts.
// It can be used for documentation, validation, or architecture tests.
public final class PublishedLanguageRegistry {

    private final Map<String, EventContractDescriptor> contracts = new HashMap<>();

    public void register(EventContractDescriptor descriptor) {
        contracts.put(descriptor.eventType() + ":v" + descriptor.version(), descriptor);
    }

    public Optional<EventContractDescriptor> find(String eventType, int version) {
        return Optional.ofNullable(contracts.get(eventType + ":v" + version));
    }
}