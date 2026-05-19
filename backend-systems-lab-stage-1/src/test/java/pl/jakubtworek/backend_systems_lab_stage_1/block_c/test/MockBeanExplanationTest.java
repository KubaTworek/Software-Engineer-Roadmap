package pl.jakubtworek.backend_systems_lab_stage_1.block_c.test;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Demonstrates @MockBean usage.
 *
 * @MockBean:
 * - creates Mockito mock,
 * - registers it inside Spring context,
 * - replaces existing bean.
 */
@WebMvcTest(UserController.class)
public class MockBeanExplanationTest {

    @MockitoBean
    private UserService userService;

    @Test
    void contextLoads() {

        /**
         * Without @MockBean,
         * Spring would fail because UserController
         * requires UserService bean.
         */
    }
}