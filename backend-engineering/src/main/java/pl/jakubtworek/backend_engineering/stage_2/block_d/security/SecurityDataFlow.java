package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import java.util.Objects;
import java.util.Set;

/** A named flow crossing one or more trust boundaries in a threat model. */
public record SecurityDataFlow(
        String name,
        TrustZone source,
        TrustZone destination,
        AccessLevel accessLevel,
        DataSensitivity sensitivity,
        Set<SecurityControl> controls) {

    public SecurityDataFlow {
        requireText(name, "name");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        Objects.requireNonNull(accessLevel, "accessLevel must not be null");
        Objects.requireNonNull(sensitivity, "sensitivity must not be null");
        controls = Set.copyOf(Objects.requireNonNull(controls, "controls must not be null"));
    }

    public enum TrustZone {
        INTERNET,
        EDGE,
        APPLICATION,
        DATA,
        THIRD_PARTY
    }

    public enum AccessLevel {
        PUBLIC,
        AUTHENTICATED,
        PRIVILEGED
    }

    public enum DataSensitivity {
        PUBLIC,
        INTERNAL,
        PII,
        SECRET;

        public boolean requiresStrongProtection() {
            return this == PII || this == SECRET;
        }
    }

    public enum SecurityControl {
        TLS,
        AUTHENTICATION,
        AUTHORIZATION,
        INPUT_VALIDATION,
        RATE_LIMIT,
        EGRESS_ALLOWLIST,
        ENCRYPTION_AT_REST,
        AUDIT
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
