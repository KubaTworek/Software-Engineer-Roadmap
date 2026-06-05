package pl.jakubtworek.chatsystem.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.BadRequestException;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;
import pl.jakubtworek.chatsystem.user.UserResponse;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase();
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email already exists");
        }

        AppUser user = new AppUser(
                username,
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim()
        );
        AppUser saved = userRepository.save(user);
        UserPrincipal principal = UserPrincipal.from(saved);
        return new AuthResponse(jwtService.generateToken(principal), "Bearer", UserResponse.from(saved));
    }

    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim().toLowerCase();
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, request.password()));

        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("Invalid username or password"));
        UserPrincipal principal = UserPrincipal.from(user);
        return new AuthResponse(jwtService.generateToken(principal), "Bearer", UserResponse.from(user));
    }
}
