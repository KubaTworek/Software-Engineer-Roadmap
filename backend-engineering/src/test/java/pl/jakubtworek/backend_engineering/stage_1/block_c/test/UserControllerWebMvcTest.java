package pl.jakubtworek.backend_engineering.stage_1.block_c.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.jakubtworek.backend_engineering.stage_1.block_c.test.User;
import pl.jakubtworek.backend_engineering.stage_1.block_c.test.UserController;
import pl.jakubtworek.backend_engineering.stage_1.block_c.test.UserService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for Spring MVC layer.
 *
 * @WebMvcTest loads:
 * - controllers,
 * - Jackson,
 * - MVC configuration,
 * - validation,
 * - filters/interceptors related to MVC.
 *
 * It DOES NOT load:
 * - repositories,
 * - services,
 * - full application context.
 */
@WebMvcTest(UserController.class)
public class UserControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Service is mocked because @WebMvcTest
     * does not load real service layer.
     */
    @MockitoBean
    private UserService userService;

    @Test
    void shouldReturnUserJson() throws Exception {

        User user = new User("John");

        when(userService.getUser(1L))
                .thenReturn(user);

        /**
         * MockMvc performs HTTP request
         * without starting real server.
         */
        mockMvc.perform(get("/users/1"))

                /**
                 * Verifies HTTP status.
                 */
                .andExpect(status().isOk())

                /**
                 * Verifies JSON response fields.
                 */
                .andExpect(jsonPath("$.name")
                        .value("John"));
    }

    @Test
    void shouldReturnValidationError() throws Exception {

        /**
         * Invalid request body:
         * name is blank.
         */
        String invalidJson = """
                {
                    "name": ""
                }
                """;

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/users")
                                .contentType("application/json")
                                .content(invalidJson)
                )

                /**
                 * Validation should return 400 BAD REQUEST.
                 */
                .andExpect(status().isBadRequest());
    }
}