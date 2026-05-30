package pl.jakubtworek.booking.service.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.security.AuthResponse;
import pl.jakubtworek.booking.entity.AppUser;
import pl.jakubtworek.booking.entity.RefreshToken;
import pl.jakubtworek.booking.exception.BusinessRuleException;
import pl.jakubtworek.booking.repository.AppUserRepository;
import pl.jakubtworek.booking.repository.RefreshTokenRepository;
import pl.jakubtworek.booking.security.JwtProperties;
import pl.jakubtworek.booking.security.JwtTokenService;
import pl.jakubtworek.booking.security.PasswordHasher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {
    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AppUserRepository appUserRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordHasher passwordEncoder,
                       JwtTokenService jwtTokenService,
                       JwtProperties jwtProperties) {
        this.appUserRepository = appUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException("Invalid credentials"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessRuleException("Invalid credentials");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        String hash = sha256(refreshTokenValue);
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessRuleException("Invalid refresh token"));
        if (!current.isActive(Instant.now())) {
            throw new BusinessRuleException("Refresh token is expired or revoked");
        }
        current.revoke();
        return issueTokens(current.getUser());
    }

    private AuthResponse issueTokens(AppUser user) {
        Instant now = Instant.now();
        String accessToken = jwtTokenService.createAccessToken(user, now);
        Instant accessTokenExpiresAt = jwtTokenService.accessTokenExpiresAt(now);
        String refreshTokenValue = randomRefreshToken();
        Instant refreshTokenExpiresAt = now.plusSeconds(jwtProperties.refreshTokenSeconds());
        refreshTokenRepository.save(new RefreshToken(user, sha256(refreshTokenValue), refreshTokenExpiresAt));
        return new AuthResponse("Bearer", accessToken, accessTokenExpiresAt, refreshTokenValue, refreshTokenExpiresAt);
    }

    private String randomRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash refresh token", exception);
        }
    }
}
