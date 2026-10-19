package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal lab adapter. It accepts only a password hash supplied from external
 * configuration; no raw demo password or credential is committed to Git.
 */
@Component
public class ConfiguredCredentialStore implements CredentialStore {

    private final Environment environment;
    private final String dummyPasswordHash;

    public ConfiguredCredentialStore(Environment environment, PasswordEncoder passwordEncoder) {
        this.environment = environment;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public Optional<UserCredentials> findByUsername(String username) {
        String configuredUsername = environment.getProperty("app.security.demo-user.username");
        String passwordHash = environment.getProperty("app.security.demo-user.password-hash");
        if (configuredUsername == null || passwordHash == null
                || !configuredUsername.equals(username)) {
            return Optional.empty();
        }
        return Optional.of(new UserCredentials(
                configuredUsername,
                passwordHash,
                true,
                List.of("USER"),
                List.of("ORDER_READ")
        ));
    }

    @Override
    public String dummyPasswordHash() {
        return dummyPasswordHash;
    }
}
