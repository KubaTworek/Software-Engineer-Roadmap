package com.example.newsfeed.auth;

import com.example.newsfeed.common.ConflictException;
import com.example.newsfeed.common.UnauthorizedException;
import com.example.newsfeed.user.User;
import com.example.newsfeed.user.UserRepository;
import com.example.newsfeed.user.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthService {

    private static final Duration TOKEN_TTL = Duration.ofDays(30);

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            AuthTokenRepository authTokenRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        String normalizedUsername = request.username().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email is already registered.");
        }

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new ConflictException("Username is already taken.");
        }

        Instant now = clock.instant();
        User user = new User(
                UUID.randomUUID(),
                normalizedEmail,
                normalizedUsername,
                request.displayName().trim(),
                passwordEncoder.encode(request.password()),
                null,
                now,
                now
        );

        User savedUser = userRepository.save(user);
        String token = createToken(savedUser);

        return new AuthResponse(token, UserResponse.from(savedUser));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password.");
        }

        String token = createToken(user);
        return new AuthResponse(token, UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public User requireUserFromAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing Bearer token.");
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new UnauthorizedException("Missing Bearer token.");
        }

        AuthToken authToken = authTokenRepository.findByToken(hashToken(token))
                .orElseThrow(() -> new UnauthorizedException("Invalid token."));

        if (authToken.isExpired(clock.instant())) {
            throw new UnauthorizedException("Token expired.");
        }

        return authToken.getUser();
    }

    private String createToken(User user) {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = clock.instant();
        AuthToken authToken = new AuthToken(
                UUID.randomUUID(),
                hashToken(token),
                user,
                now,
                now.plus(TOKEN_TTL)
        );

        authTokenRepository.save(authToken);
        return token;
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
