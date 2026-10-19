package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.degradation;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry for operational emergency levers.
 *
 * In production this would usually be backed by a feature flag system
 * or dynamic configuration service.
 */
public class EmergencyLeverRegistry {

    private final Set<EmergencyLever> enabled = ConcurrentHashMap.newKeySet();

    public void enable(EmergencyLever lever) {
        if (lever == null) throw new IllegalArgumentException("lever is required");
        enabled.add(lever);
    }

    public void disable(EmergencyLever lever) {
        if (lever == null) throw new IllegalArgumentException("lever is required");
        enabled.remove(lever);
    }

    public boolean isEnabled(EmergencyLever lever) {
        if (lever == null) throw new IllegalArgumentException("lever is required");
        return enabled.contains(lever);
    }

    public Set<EmergencyLever> enabledLevers() {
        if (enabled.isEmpty()) {
            return EnumSet.noneOf(EmergencyLever.class);
        }

        return EnumSet.copyOf(enabled);
    }
}
