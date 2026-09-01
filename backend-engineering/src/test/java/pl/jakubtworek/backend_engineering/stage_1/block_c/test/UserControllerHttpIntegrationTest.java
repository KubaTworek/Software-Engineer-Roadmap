package pl.jakubtworek.backend_engineering.stage_1.block_c.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test with real HTTP server.
 *
 * RANDOM_PORT starts embedded server
 * on random TCP port.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureTestRestTemplate
@Import(UserControllerHttpIntegrationTest.PermitAllTestSecurity.class)
public class UserControllerHttpIntegrationTest {

    @TestConfiguration
    static class PermitAllTestSecurity {

        @Bean
        @Order(0)
        SecurityFilterChain permitAllTestRequests(HttpSecurity http) throws Exception {
            return http
                    .securityMatcher("/users/**")
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldCallRealHttpEndpoint() {

        CreateUserRequest request =
                new CreateUserRequest("John");

        ResponseEntity<UserResponse> response =
                restTemplate.postForEntity(
                        "http://localhost:" + port + "/users",
                        request,
                        UserResponse.class
                );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name())
                .isEqualTo("John");
    }
}
