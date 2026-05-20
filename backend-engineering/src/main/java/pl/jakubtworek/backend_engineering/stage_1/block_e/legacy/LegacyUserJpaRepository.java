package pl.jakubtworek.backend_engineering.stage_1.block_e.legacy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LegacyUserJpaRepository extends JpaRepository<LegacyUserEntity, Long> {
    boolean existsByEmail(String email);
}
