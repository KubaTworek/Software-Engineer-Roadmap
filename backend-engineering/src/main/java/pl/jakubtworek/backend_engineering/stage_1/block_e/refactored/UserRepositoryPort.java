package pl.jakubtworek.backend_engineering.stage_1.block_e.refactored;

import java.util.Optional;

public interface UserRepositoryPort {
    boolean existsByEmail(String email);
    User save(User user);
    Optional<User> findByEmail(String email);
}
