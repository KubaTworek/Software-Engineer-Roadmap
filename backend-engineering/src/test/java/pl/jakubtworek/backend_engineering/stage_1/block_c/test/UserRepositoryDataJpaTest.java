package pl.jakubtworek.backend_engineering.stage_1.block_c.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import pl.jakubtworek.backend_engineering.stage_1.block_c.test.User;
import pl.jakubtworek.backend_engineering.stage_1.block_c.test.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice test for JPA layer.
 *
 * @DataJpaTest loads:
 * - EntityManager,
 * - Hibernate,
 * - repositories,
 * - embedded database (usually H2).
 *
 * It DOES NOT load:
 * - controllers,
 * - web layer,
 * - full application context.
 *
 * Every test runs in transaction
 * and rolls back automatically.
 */
@DataJpaTest
public class UserRepositoryDataJpaTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldFindUserByName() {

        /**
         * Data inserted into test database.
         */
        userRepository.save(new User("John"));

        Optional<User> user =
                userRepository.findByName("John");

        assertThat(user).isPresent();

        assertThat(user.get().getName())
                .isEqualTo("John");
    }
}