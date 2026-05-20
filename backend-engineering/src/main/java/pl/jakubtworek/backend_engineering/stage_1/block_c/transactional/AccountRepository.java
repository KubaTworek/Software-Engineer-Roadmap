package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository managed by Spring.
 *
 * Repository methods are usually transactional by default,
 * but business transactions should be controlled at service layer.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {
}