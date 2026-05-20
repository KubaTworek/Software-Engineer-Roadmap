package pl.jakubtworek.backend_engineering.stage_1.block_c.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.backend_engineering.stage_1.block_c.test.User;
import pl.jakubtworek.backend_engineering.stage_1.block_c.test.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test with transaction rollback.
 *
 * Transaction is rolled back after test finishes.
 *
 * Useful for keeping test database clean.
 */
@SpringBootTest
@Transactional
public class TransactionalIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldRollbackTransactionAfterTest() {

        userRepository.save(new User("RollbackUser"));

        long count = userRepository.count();

        assertThat(count).isGreaterThan(0);

        /**
         * After test finishes,
         * transaction is rolled back automatically.
         */
    }
}