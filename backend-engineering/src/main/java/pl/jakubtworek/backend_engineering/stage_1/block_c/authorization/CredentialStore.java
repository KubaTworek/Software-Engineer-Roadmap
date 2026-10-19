package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import java.util.Optional;

public interface CredentialStore {

    Optional<UserCredentials> findByUsername(String username);

    /** Valid hash used to perform comparable password work for an unknown user. */
    String dummyPasswordHash();
}
