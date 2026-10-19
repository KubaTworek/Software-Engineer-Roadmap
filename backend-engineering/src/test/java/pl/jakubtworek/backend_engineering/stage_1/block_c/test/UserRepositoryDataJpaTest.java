package pl.jakubtworek.backend_engineering.stage_1.block_c.test;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
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
@DataJpaTest(properties = "spring.data.jpa.repositories.enabled=false")
public class UserRepositoryDataJpaTest {

    @TestConfiguration
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    static class RepositoryTestConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldFindUserByName() {

        /**
         * Data inserted into test database.
         */
        userRepository.saveAndFlush(new User("John"));
        entityManager.clear();

        Optional<User> user =
                userRepository.findByName("John");

        assertThat(user).isPresent();

        assertThat(user.get().getName())
                .isEqualTo("John");
    }

    @Test
    void shouldReturnEmptyForANameOutsideTheDatabase() {
        assertThat(userRepository.findByName("missing")).isEmpty();
    }
}
