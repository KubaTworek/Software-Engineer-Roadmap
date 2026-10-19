package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
    private final CredentialStore credentialStore;

    public AuthService(
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            CredentialStore credentialStore
    ) {
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.credentialStore = credentialStore;
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

        var foundCredentials = credentialStore.findByUsername(request.username());
        String passwordHash = foundCredentials
                .map(UserCredentials::passwordHash)
                .orElseGet(credentialStore::dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        UserCredentials credentials = foundCredentials.orElse(null);
        if (credentials == null || !credentials.enabled() || !passwordMatches) {
            // The same outward error for an unknown, disabled or mistyped account
            // and comparable password work reduce username enumeration signals.
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtTokenService.generateAccessToken(
                credentials.username(),
                credentials.roles(),
                credentials.permissions()
        );

        String refreshToken =
                refreshTokenService.createRefreshToken(credentials.username());

        return new TokenResponse(
                accessToken,
                refreshToken,
                credentials.roles(),
                credentials.permissions()
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

        RotatedRefreshToken rotatedToken =
                refreshTokenService.rotateRefreshToken(request.refreshToken());

        UserCredentials credentials = credentialStore.findByUsername(rotatedToken.username())
                .filter(UserCredentials::enabled)
                .orElseThrow(InvalidCredentialsException::new);

        String newAccessToken = jwtTokenService.generateAccessToken(
                rotatedToken.username(),
                credentials.roles(),
                credentials.permissions()
        );

        return new TokenResponse(
                newAccessToken,
                rotatedToken.rawToken(),
                credentials.roles(),
                credentials.permissions()
        );
    }
}
