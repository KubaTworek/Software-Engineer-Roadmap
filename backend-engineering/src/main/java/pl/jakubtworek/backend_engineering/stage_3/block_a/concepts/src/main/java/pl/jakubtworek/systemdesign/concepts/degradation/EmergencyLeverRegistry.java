package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.degradation;

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
        enabled.add(lever);
    }

    public void disable(EmergencyLever lever) {
        enabled.remove(lever);
    }

    public boolean isEnabled(EmergencyLever lever) {
        return enabled.contains(lever);
    }

    public Set<EmergencyLever> enabledLevers() {
        if (enabled.isEmpty()) {
            return EnumSet.noneOf(EmergencyLever.class);
        }

        return EnumSet.copyOf(enabled);
    }
}