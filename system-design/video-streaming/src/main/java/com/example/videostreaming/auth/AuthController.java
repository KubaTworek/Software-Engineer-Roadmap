package com.example.videostreaming.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static com.example.videostreaming.auth.AuthDtos.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().toLowerCase();
        if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), UserRole.USER));
        return response(user);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        User user = users.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return response(user);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getRole());
    }

    private AuthResponse response(User user) {
        return new AuthResponse(jwtService.issue(user), "Bearer", user.getId(), user.getEmail(), user.getRole());
    }
}
