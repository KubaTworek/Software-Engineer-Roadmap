package pl.jakubtworek.chatsystem.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.BadRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {
    @Autowired AuthService authService;

    @Test
    void registerNormalizesUsernameAndEmailAndReturnsToken() {
        AuthResponse response = authService.register(new RegisterRequest(
                "  AliceTest  ",
                "  ALICE.TEST@example.COM  ",
                "very-secret-password",
                "Alice"
        ));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().username()).isEqualTo("alicetest");
        assertThat(response.user().email()).isEqualTo("alice.test@example.com");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        authService.register(new RegisterRequest("bobtest", "bobtest@example.com", "very-secret-password", "Bob"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "BOBTEST", "bobtest2@example.com", "very-secret-password", "Bob 2"
        ))).isInstanceOf(BadRequestException.class)
          .hasMessageContaining("Username already exists");
    }

    @Test
    void loginReturnsJwtForRegisteredUser() {
        authService.register(new RegisterRequest("caroltest", "caroltest@example.com", "very-secret-password", "Carol"));

        AuthResponse response = authService.login(new LoginRequest("caroltest", "very-secret-password"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().username()).isEqualTo("caroltest");
    }
}
