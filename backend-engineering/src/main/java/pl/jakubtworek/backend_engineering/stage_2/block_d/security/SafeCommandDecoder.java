package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import java.util.Map;
import java.util.Set;

/**
 * Allowlisted discriminator decoder. It never turns an input class name into
 * reflection or native Java deserialization.
 */
public final class SafeCommandDecoder {

    public Command decode(Map<String, String> payload) {
        if (payload == null || payload.size() > 3) throw rejected("payload shape is invalid");
        String type = payload.get("type");
        if ("email-change".equals(type)) {
            requireExactKeys(payload, Set.of("type", "email"));
            String email = payload.get("email");
            if (email == null || !email.matches("[^@\\s]{1,64}@[^@\\s]{1,190}")) throw rejected("email is invalid");
            return new EmailChange(email);
        }
        if ("profile-update".equals(type)) {
            requireExactKeys(payload, Set.of("type", "displayName"));
            String displayName = payload.get("displayName");
            if (displayName == null || displayName.isBlank() || displayName.length() > 80) throw rejected("displayName is invalid");
            return new ProfileUpdate(displayName);
        }
        throw rejected("command type is not allowlisted");
    }

    private static void requireExactKeys(Map<String, String> payload, Set<String> keys) {
        if (!payload.keySet().equals(keys)) throw rejected("unknown or missing fields");
    }

    private static UnsafeCommandException rejected(String reason) {
        return new UnsafeCommandException(reason);
    }

    public sealed interface Command permits EmailChange, ProfileUpdate {
    }

    public record EmailChange(String email) implements Command {
    }

    public record ProfileUpdate(String displayName) implements Command {
    }

    public static final class UnsafeCommandException extends RuntimeException {
        public UnsafeCommandException(String message) {
            super(message);
        }
    }
}
