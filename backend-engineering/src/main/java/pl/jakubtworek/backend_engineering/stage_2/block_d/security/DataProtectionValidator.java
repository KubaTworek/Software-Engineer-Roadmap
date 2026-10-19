package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import java.util.ArrayList;
import java.util.List;

public final class DataProtectionValidator {

    public List<String> violations(DataProtectionBoundary boundary) {
        if (boundary == null) throw new IllegalArgumentException("boundary is required");
        if (!boundary.sensitivity().requiresStrongProtection()) return List.of();
        List<String> violations = new ArrayList<>();
        for (DataProtectionBoundary.TransportHop hop : boundary.transportHops()) {
            if (!hop.encrypted()) violations.add("unencrypted transport hop: " + hop.name());
            if (hop.certificateOwner() == null || hop.certificateOwner().isBlank()) {
                violations.add("transport certificate owner missing: " + hop.name());
            }
        }
        for (DataProtectionBoundary.StorageCopy copy : boundary.storageCopies()) {
            if (!copy.encrypted()) violations.add("unencrypted storage copy: " + copy.name());
            if (copy.keyOwner() == null || copy.keyOwner().isBlank()) {
                violations.add("storage key owner missing: " + copy.name());
            }
        }
        return List.copyOf(violations);
    }
}
