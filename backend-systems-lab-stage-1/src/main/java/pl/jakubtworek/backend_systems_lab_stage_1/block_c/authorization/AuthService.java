package pl.jakubtworek.backend_systems_lab_stage_1.block_c.authorization;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Authentication service.
 *
 * In a real application, user should be loaded from database.
 */
@Service
public class AuthService {

    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder
    ) {
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Simulates login process.
     *
     * Real flow:
     * - find user by username,
     * - verify password using PasswordEncoder,
     * - generate access token,
     * - generate refresh token.
     */
    public TokenResponse login(LoginRequest request) {

        /**
         * Never compare raw passwords manually in production.
         */
        boolean passwordValid = passwordEncoder.matches(
                request.password(),
                "$2a$10$exampleHash"
        );

        if (!passwordValid) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        List<String> roles = List.of("USER");
        List<String> permissions = List.of("ORDER_READ");

        String accessToken = jwtTokenService.generateAccessToken(
                request.username(),
                roles,
                permissions
        );

        String refreshToken =
                refreshTokenService.createRefreshToken(request.username());

        return new TokenResponse(
                accessToken,
                refreshToken,
                roles,
                permissions
        );
    }

    /**
     * Refresh flow.
     *
     * Old refresh token is invalidated.
     * New refresh token is issued.
     * New access token is issued.
     */
    public TokenResponse refresh(RefreshTokenRequest request) {

        String newRefreshToken =
                refreshTokenService.rotateRefreshToken(request.refreshToken());

        /**
         * In real code, username should be returned from refresh token validation.
         * This is simplified for demonstration.
         */
        String username = "resolved-user";

        List<String> roles = List.of("USER");
        List<String> permissions = List.of("ORDER_READ");

        String newAccessToken = jwtTokenService.generateAccessToken(
                username,
                roles,
                permissions
        );

        return new TokenResponse(
                newAccessToken,
                newRefreshToken,
                roles,
                permissions
        );
    }
}