package com.ridesharing.mvp.auth;

import com.ridesharing.mvp.common.ApiException;
import com.ridesharing.mvp.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthController.AuthResponse register(AuthController.RegisterRequest request) {
        if (users.existsByEmail(request.email())) throw new ApiException(HttpStatus.CONFLICT, "Email already exists");
        if (users.existsByPhoneNumber(request.phoneNumber())) throw new ApiException(HttpStatus.CONFLICT, "Phone number already exists");
        var now = Instant.now();
        var user = AppUser.builder()
                .id(UUID.randomUUID())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(request.role())
                .status(UserStatus.ACTIVE)
                .rating(BigDecimal.valueOf(5.00))
                .createdAt(now)
                .updatedAt(now)
                .build();
        users.save(user);
        return response(user);
    }

    public AuthController.AuthResponse login(AuthController.LoginRequest request) {
        var user = users.findByEmail(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return response(user);
    }

    private AuthController.AuthResponse response(AppUser user) {
        return new AuthController.AuthResponse(jwtService.generate(user), "Bearer", user.getId().toString(), user.getRole().name());
    }
}
