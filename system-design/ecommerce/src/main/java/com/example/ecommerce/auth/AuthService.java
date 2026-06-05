package com.example.ecommerce.auth;

import com.example.ecommerce.auth.dto.AuthDtos;
import com.example.ecommerce.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final long tokenTtlHours;

    public AuthService(
            AppUserRepository users,
            AuthTokenRepository tokens,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.token-ttl-hours:24}") long tokenTtlHours
    ) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenTtlHours = tokenTtlHours;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("User with this email already exists");
        }

        AppUser user = new AppUser(
                request.email().toLowerCase(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                Set.of(UserRole.CUSTOMER)
        );

        users.save(user);
        return issueToken(user);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        AppUser user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User is disabled");
        }

        return issueToken(user);
    }

    public AuthDtos.UserResponse toResponse(AppUser user) {
        return new AuthDtos.UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRoles());
    }

    private AuthDtos.AuthResponse issueToken(AppUser user) {
        String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        AuthToken token = new AuthToken(rawToken, user, Instant.now().plus(tokenTtlHours, ChronoUnit.HOURS));
        tokens.save(token);
        return new AuthDtos.AuthResponse(rawToken, toResponse(user));
    }
}
