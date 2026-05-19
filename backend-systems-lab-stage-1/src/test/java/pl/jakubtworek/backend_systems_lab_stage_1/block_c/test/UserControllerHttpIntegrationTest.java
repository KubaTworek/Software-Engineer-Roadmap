package pl.jakubtworek.backend_systems_lab_stage_1.block_c.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;

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
public class UserControllerHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldCallRealHttpEndpoint() {

        CreateUserRequest request =
                new CreateUserRequest("John");

        UserResponse response =
                restTemplate.postForObject(
                        "http://localhost:" + port + "/users",
                        request,
                        UserResponse.class
                );

        assertThat(response.name())
                .isEqualTo("John");
    }
}