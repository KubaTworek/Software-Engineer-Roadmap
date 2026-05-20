package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.springframework.web.bind.annotation.*;

/**
 * Public authentication controller.
 *
 * Login and refresh endpoints are usually public,
 * but refresh endpoint must still validate refresh token.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * User logs in and receives access token + refresh token.
     */
    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Client exchanges refresh token for new tokens.
     */
    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }
}