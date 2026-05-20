package pl.jakubtworek.backend_engineering.stage_1.block_c.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.jakubtworek.backend_engineering.stage_1.block_c.test.User;
import pl.jakubtworek.backend_engineering.stage_1.block_c.test.UserRepository;

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

        assert user.getId() != null;
    }
}