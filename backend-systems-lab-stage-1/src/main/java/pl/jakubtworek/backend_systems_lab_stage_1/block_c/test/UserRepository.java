package pl.jakubtworek.backend_systems_lab_stage_1.block_c.test;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository.
 */
public interface UserRepository
        extends JpaRepository<User, Long> {

    /**
     * Query derivation example.
     */
    Optional<User> findByName(String name);
}