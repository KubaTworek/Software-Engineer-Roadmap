package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final CredentialStore credentialStore = mock(CredentialStore.class);
    private final AuthService authService = new AuthService(
            jwtTokenService,
            refreshTokenService,
            passwordEncoder,
            credentialStore
    );

    @Test
    void shouldIssueTokensAfterSuccessfulLogin() {
        UserCredentials credentials = credentials();
        when(credentialStore.findByUsername("alice")).thenReturn(Optional.of(credentials));
        when(passwordEncoder.matches("secret", "stored-hash")).thenReturn(true);
        when(jwtTokenService.generateAccessToken("alice", java.util.List.of("USER"), java.util.List.of("ORDER_READ")))
                .thenReturn("access-token");
        when(refreshTokenService.createRefreshToken("alice")).thenReturn("refresh-token");

        TokenResponse response = authService.login(new LoginRequest("alice", "secret"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void shouldRejectInvalidCredentialsWithoutIssuingTokens() {
        when(credentialStore.findByUsername("alice")).thenReturn(Optional.of(credentials()));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password");
        verify(refreshTokenService, never()).createRefreshToken("alice");
    }

    @Test
    void shouldUseTheSameFailureForAnUnknownUsername() {
        when(credentialStore.findByUsername("unknown")).thenReturn(Optional.empty());
        when(credentialStore.dummyPasswordHash()).thenReturn("dummy-hash");
        when(passwordEncoder.matches("wrong", "dummy-hash")).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> authService.login(new LoginRequest("unknown", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password");
        verify(passwordEncoder).matches("wrong", "dummy-hash");
    }

    @Test
    void shouldRejectDisabledAccount() {
        UserCredentials disabled = new UserCredentials(
                "alice", "stored-hash", false, List.of("USER"), List.of("ORDER_READ")
        );
        when(credentialStore.findByUsername("alice")).thenReturn(Optional.of(disabled));
        when(passwordEncoder.matches("secret", "stored-hash")).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> authService.login(new LoginRequest("alice", "secret")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordEncoder).matches("secret", "stored-hash");
    }

    @Test
    void shouldIssueAccessTokenForRefreshTokenOwner() {
        when(refreshTokenService.rotateRefreshToken("old-token"))
                .thenReturn(new RotatedRefreshToken("new-token", "alice"));
        when(credentialStore.findByUsername("alice")).thenReturn(Optional.of(credentials()));
        when(jwtTokenService.generateAccessToken(org.mockito.ArgumentMatchers.eq("alice"), anyList(), anyList()))
                .thenReturn("new-access-token");

        TokenResponse response = authService.refresh(new RefreshTokenRequest("old-token"));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-token");
        verify(jwtTokenService).generateAccessToken(
                "alice",
                java.util.List.of("USER"),
                java.util.List.of("ORDER_READ")
        );
    }

    private static UserCredentials credentials() {
        return new UserCredentials(
                "alice",
                "stored-hash",
                true,
                List.of("USER"),
                List.of("ORDER_READ")
        );
    }
}
