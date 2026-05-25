package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.degradation;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory feature toggle registry for operational levers.
 *
 * In production, this would usually be backed by a dynamic configuration system,
 * feature flag platform, or control plane.
 */
public class EmergencyLeverRegistry {

    private final Set<EmergencyLever> enabledLevers = ConcurrentHashMap.newKeySet();

    public void enable(EmergencyLever lever) {
        enabledLevers.add(lever);
    }

    public void disable(EmergencyLever lever) {
        enabledLevers.remove(lever);
    }

    public boolean isEnabled(EmergencyLever lever) {
        return enabledLevers.contains(lever);
    }

    public Set<EmergencyLever> enabledLevers() {
        if (enabledLevers.isEmpty()) {
            return EnumSet.noneOf(EmergencyLever.class);
        }

        return EnumSet.copyOf(enabledLevers);
    }
}