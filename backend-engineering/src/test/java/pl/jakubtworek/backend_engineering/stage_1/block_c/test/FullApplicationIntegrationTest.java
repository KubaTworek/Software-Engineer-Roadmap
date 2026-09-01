package pl.jakubtworek.backend_engineering.stage_1.block_c.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration test.
 *
 * @SpringBootTest loads entire application context:
 * - controllers,
 * - services,
 * - repositories,
 * - security,
 * - configuration,
 * - full Spring Boot infrastructure.
 *
 * This is the slowest type of test,
 * but closest to real application behavior.
 */
@SpringBootTest
public class FullApplicationIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldLoadFullContextAndSaveUser() {

        User user = userRepository.save(
                new User("IntegrationUser")
        );

        assertThat(user.getId()).isNotNull();
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(user.getId());
        assertThat(reloaded.getName()).isEqualTo("IntegrationUser");
    }
}
