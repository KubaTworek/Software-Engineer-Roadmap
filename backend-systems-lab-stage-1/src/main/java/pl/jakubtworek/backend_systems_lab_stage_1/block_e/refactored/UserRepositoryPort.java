package pl.jakubtworek.backend_systems_lab_stage_1.block_e.refactored;

import java.util.Optional;

public interface UserRepositoryPort {
    boolean existsByEmail(String email);
    User save(User user);
    Optional<User> findByEmail(String email);
}
